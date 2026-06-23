package com.uninsubria.derma_bsa.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer

object OnnxHelper {

    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()

    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std  = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun init(context: Context) {
        if (session != null) return

        val modelFile = File(context.filesDir, "derma_seg.onnx")
        val dataFile = File(context.filesDir, "derma_seg.onnx.data")

        if (!modelFile.exists()) {
            context.assets.open("derma_seg.onnx").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!dataFile.exists()) {
            context.assets.open("derma_seg.onnx.data").use { input ->
                dataFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        session = env.createSession(modelFile.absolutePath)
    }

    fun segment(bitmap: Bitmap): Bitmap {
        val size = 256
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)

        val floatBuf = FloatBuffer.allocate(1 * 3 * size * size)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF) / 255f
            val g = ((pixels[i] shr 8)  and 0xFF) / 255f
            val b = ((pixels[i])        and 0xFF) / 255f
            floatBuf.put(i,                   (r - mean[0]) / std[0])
            floatBuf.put(i + size * size,     (g - mean[1]) / std[1])
            floatBuf.put(i + 2 * size * size, (b - mean[2]) / std[2])
        }

        val tensor = OnnxTensor.createTensor(env, floatBuf, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val output = session!!.run(mapOf("input" to tensor))

        // Output shape: [1, 1, 256, 256]
        val raw = output[0].value as Array<Array<Array<FloatArray>>>

        val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val prob = 1f / (1f + Math.exp(-raw[0][0][y][x].toDouble())).toFloat()
                val color = if (prob > 0.5f) 0x99FF0000.toInt() else 0x00000000
                maskBitmap.setPixel(x, y, color)
            }
        }

        tensor.close()
        output.close()

        return maskBitmap
    }

    fun calcBsa(mask: Bitmap, regionBsaPercent: Float): Float {
        val size = 256 * 256
        var white = 0
        for (y in 0 until mask.height)
            for (x in 0 until mask.width)
                if ((mask.getPixel(x, y) shr 24 and 0xFF) > 0) white++
        return regionBsaPercent * (white.toFloat() / size)
    }
}