package com.uninsubria.derma_bsa.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
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
        val output = session!!.run(mapOf("input" to tensor))

        @Suppress("UNCHECKED_CAST")
        val raw = (output[0].value as Array<Array<Array<FloatArray>>>)[0][0]

        tensor.close()
        output.close()

        val outPx = IntArray(size * size) { i ->
            if (sigmoid(raw[i / size][i % size]) > 0.5f) 0x77FF0000.toInt() else 0x00000000
        }
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        mask.setPixels(outPx, 0, size, 0, 0, size, size)
        return mask
    }

    /**
     * Esegue la segmentazione limitandola all'area selezionata manualmente dall'utente.
     *
     * Se [selectionMask] non è `null`, i pixel dell'immagine che cadono fuori dalla
     * maschera vengono sostituiti con nero prima di passare il bitmap a [segment].
     * In questo modo il modello "vede" solo la zona di interesse.
     * Se [selectionMask] è `null` la segmentazione viene eseguita sull'intera immagine.
     *
     * @param bitmap immagine ritagliata da analizzare
     * @param selectionMask maschera bianca su trasparente disegnata dall'utente, oppure `null`
     * @return maschera 512×512 con le lesioni evidenziate in rosso
     */
    fun segmentWithMask(bitmap: Bitmap, selectionMask: Bitmap?): Bitmap {
        if (selectionMask == null) return segment(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val scaledMask = Bitmap.createScaledBitmap(selectionMask, w, h, true)

        val maskPx = IntArray(w * h)
        scaledMask.getPixels(maskPx, 0, w, 0, 0, w, h)

        val srcPx = IntArray(w * h)
        bitmap.getPixels(srcPx, 0, w, 0, 0, w, h)

        for (i in maskPx.indices) {
            if ((maskPx[i] ushr 24) == 0) srcPx[i] = 0xFF000000.toInt()
        }

        val masked = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        masked.setPixels(srcPx, 0, w, 0, 0, w, h)
        return segment(masked)
    }

    /**
     * Calcola il contributo percentuale al BSA totale del distretto misurato.
     *
     * @param mask maschera 512×512 prodotta da [segment] o [segmentWithMask]
     * @param regionBsaPercent percentuale BSA del distretto (es. 18.0 per il tronco)
     * @return contributo BSA del distretto in percentuale (0–regionBsaPercent)
     */
    fun calcBsa(mask: Bitmap, regionBsaPercent: Float): Float {
        var lesionPixels = 0
        for (y in 0 until mask.height)
            for (x in 0 until mask.width)
                if ((mask.getPixel(x, y) shr 24 and 0xFF) > 0) lesionPixels++
        return regionBsaPercent * (lesionPixels.toFloat() / (mask.width * mask.height))
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))
}