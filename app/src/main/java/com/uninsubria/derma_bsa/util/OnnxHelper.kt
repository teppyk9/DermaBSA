package com.uninsubria.derma_bsa.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Singleton che gestisce il modello ONNX di segmentazione delle lesioni cutanee.
 *
 * Il modello `derma_seg_v2.onnx` accetta in input un'immagine 512×512 normalizzata
 * con i valori ImageNet e restituisce una mappa di probabilità della stessa dimensione,
 * dove ogni pixel indica quanto è probabile che sia una lesione psoriasica.
 *
 * Prima di chiamare qualsiasi altra funzione è necessario chiamare [init].
 */
object OnnxHelper {

    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()

    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std  = floatArrayOf(0.229f, 0.224f, 0.225f)

    private const val MODEL_SIZE = 512

    /**
     * Carica il modello ONNX dal percorso `filesDir` del contesto, copiandolo
     * prima dagli asset se non è ancora presente sul disco.
     * La funzione è idempotente: se la sessione è già stata creata non fa nulla.
     *
     * @param context contesto Android usato per accedere ad asset e filesDir
     */
    fun init(context: Context) {
        if (session != null) return

        val modelFile = File(context.filesDir, "derma_seg_v2.onnx")
        val dataFile  = File(context.filesDir, "derma_seg_v2.onnx.data")

        if (!modelFile.exists()) {
            context.assets.open("derma_seg_v2.onnx").use { it.copyTo(modelFile.outputStream()) }
        }
        if (!dataFile.exists()) {
            context.assets.open("derma_seg_v2.onnx.data").use { it.copyTo(dataFile.outputStream()) }
        }

        session = env.createSession(modelFile.absolutePath)
    }

    /**
     * Esegue la segmentazione delle lesioni sull'immagine fornita.
     *
     * L'immagine viene ridimensionata a 512×512, normalizzata con i valori ImageNet
     * e passata al modello. Il risultato è una bitmap 512×512 in cui i pixel rossi
     * semitrasparenti (`0x77FF0000`) indicano le aree classificate come lesioni.
     *
     * @param bitmap immagine di input (qualsiasi risoluzione)
     * @return maschera 512×512 con le lesioni evidenziate in rosso
     */
    fun segment(bitmap: Bitmap): Bitmap {
        val sess = session ?: throw IllegalStateException("Modello ONNX non inizializzato")
        val size = MODEL_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)

        val floatBuf = FloatBuffer.allocate(3 * size * size)
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)

        for (i in px.indices) {
            val r = ((px[i] shr 16) and 0xFF) / 255f
            val g = ((px[i] shr 8)  and 0xFF) / 255f
            val b = (px[i]           and 0xFF) / 255f
            floatBuf.put(i,                   (r - mean[0]) / std[0])
            floatBuf.put(i + size * size,     (g - mean[1]) / std[1])
            floatBuf.put(i + 2 * size * size, (b - mean[2]) / std[2])
        }

        val tensor = OnnxTensor.createTensor(env, floatBuf, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val output = sess.run(mapOf("input" to tensor))

        @Suppress("UNCHECKED_CAST")
        val raw = (output[0].value as Array<Array<Array<FloatArray>>>)[0][0]

        tensor.close()
        output.close()

        val sigmoidValues = FloatArray(size * size) { i -> sigmoid(raw[i / size][i % size]) }
        val threshold = adaptiveThreshold(sigmoidValues)

        val outPx = IntArray(size * size) { i ->
            if (sigmoidValues[i] > threshold) 0x77FF0000.toInt() else 0x00000000
        }
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        mask.setPixels(outPx, 0, size, 0, 0, size, size)
        return mask
    }

    /**
     * Esegue la segmentazione limitandola all'area selezionata manualmente dall'utente.
     *
     * Quando è presente una [selectionMask], calcola il bounding box minimo che contiene
     * tutta l'area disegnata e ritaglia l'immagine a quella zona. Il ritaglio viene poi
     * scalato a 512×512 prima di passare al modello, in modo che tutti i pixel di input
     * siano usati per l'area di interesse anziché per sfondo nero.
     *
     * Il risultato viene riproiettato nelle coordinate dell'immagine originale e restituito
     * come bitmap delle stesse dimensioni di [bitmap], così il calcolo BSA rimane corretto.
     *
     * Se [selectionMask] è `null` la segmentazione viene eseguita sull'intera immagine.
     *
     * @param bitmap immagine ritagliata da analizzare
     * @param selectionMask maschera bianca su trasparente disegnata dall'utente, oppure `null`
     * @return maschera delle stesse dimensioni di [bitmap] con le lesioni evidenziate in rosso
     */
    fun segmentWithMask(bitmap: Bitmap, selectionMask: Bitmap?): Bitmap {
        if (selectionMask == null) return segment(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val scaledMask = Bitmap.createScaledBitmap(selectionMask, w, h, true)

        val maskPx = IntArray(w * h)
        scaledMask.getPixels(maskPx, 0, w, 0, 0, w, h)

        var minX = w; var maxX = 0
        var minY = h; var maxY = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((maskPx[y * w + x] ushr 24) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (minX > maxX || minY > maxY) return segment(bitmap)

        val bboxW = maxX - minX + 1
        val bboxH = maxY - minY + 1

        val srcPx = IntArray(w * h)
        bitmap.getPixels(srcPx, 0, w, 0, 0, w, h)
        for (i in maskPx.indices) {
            if ((maskPx[i] ushr 24) == 0) srcPx[i] = 0xFF000000.toInt()
        }

        val bboxPx = IntArray(bboxW * bboxH)
        for (y in 0 until bboxH)
            for (x in 0 until bboxW)
                bboxPx[y * bboxW + x] = srcPx[(minY + y) * w + (minX + x)]

        val bboxBitmap = Bitmap.createBitmap(bboxW, bboxH, Bitmap.Config.ARGB_8888)
        bboxBitmap.setPixels(bboxPx, 0, bboxW, 0, 0, bboxW, bboxH)

        val bboxMask = segment(bboxBitmap)

        val scaledResult = Bitmap.createScaledBitmap(bboxMask, bboxW, bboxH, false)
        val fullMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(fullMask).drawBitmap(scaledResult, minX.toFloat(), minY.toFloat(), null)

        return fullMask
    }

    /**
     * Calcola il contributo percentuale al BSA totale del distretto misurato.
     *
     * Conta i pixel con alpha > 0 nella maschera (lesioni rilevate) e ne calcola
     * la proporzione rispetto all'area totale della maschera. Funziona correttamente
     * sia con maschere 512×512 (auto detect) sia con maschere a dimensione originale
     * (selezione manuale con bounding box).
     *
     * @param mask maschera prodotta da [segment] o [segmentWithMask]
     * @param regionBsaPercent percentuale BSA del distretto (es. 18.0 per il tronco)
     * @return contributo BSA del distretto in percentuale (0–regionBsaPercent)
     */
    fun calcBsa(mask: Bitmap, regionBsaPercent: Float): Float {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        val lesionPixels = pixels.count { (it ushr 24) > 0 }
        return regionBsaPercent * (lesionPixels.toFloat() / pixels.size)
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    /**
     * Calcola una soglia di binarizzazione adattiva con il metodo di Otsu sui valori
     * sigmoid prodotti dal modello, anziché usare 0.5 fisso.
     *
     * Otsu trova la soglia che minimizza la varianza intra-classe tra i due gruppi
     * (sfondo e lesione), cercando la separazione ottimale nella distribuzione
     * dei valori di confidenza. Il risultato viene bloccato nell'intervallo [0.2, 0.4]
     * per evitare falsi negativi su lesioni a basso contrasto cromatico.
     *
     * @param values array di valori sigmoid in [0, 1], uno per pixel
     * @return soglia in [0.2, 0.4]
     */
    private fun adaptiveThreshold(values: FloatArray, numBins: Int = 256): Float {
        val hist = IntArray(numBins)
        for (v in values) {
            val bin = (v * (numBins - 1)).toInt().coerceIn(0, numBins - 1)
            hist[bin]++
        }

        val total = values.size.toFloat()
        var sumAll = 0f
        for (i in hist.indices) sumAll += i * hist[i]

        var sumBackground = 0f
        var countBackground = 0
        var bestVariance = 0f
        var bestThreshold = 0.3f

        for (t in hist.indices) {
            countBackground += hist[t]
            if (countBackground == 0) continue
            val countForeground = total - countBackground
            if (countForeground <= 0f) break

            sumBackground += t * hist[t]
            val meanBackground = sumBackground / countBackground
            val meanForeground = (sumAll - sumBackground) / countForeground

            val variance = countBackground * countForeground *
                    (meanBackground - meanForeground) * (meanBackground - meanForeground)

            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = t.toFloat() / (numBins - 1)
            }
        }

        return bestThreshold.coerceIn(0.2f, 0.4f)
    }
}