package com.uninsubria.derma_bsa.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * View personalizzata che permette all'utente di disegnare la zona da analizzare
 * direttamente sull'immagine, usando un pennello o una gomma.
 *
 * Internamente mantiene un [Bitmap] trasparente (la maschera) su cui vengono
 * registrati i tratti dell'utente. La maschera ha pixel bianchi opachi dove
 * l'utente ha disegnato e pixel trasparenti nel resto.
 * Chiamare [getSelectionMask] per recuperarla e passarla a OnnxHelper.
 */
class SelectionCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * Strumento attivo per il disegno sulla maschera.
     */
    enum class Tool {
        /** Aggiunge area alla selezione. */
        BRUSH,
        /** Rimuove area dalla selezione. */
        ERASER
    }

    /** Strumento attualmente in uso. */
    var activeTool: Tool = Tool.BRUSH

    /** Dimensione in pixel del pennello o della gomma. */
    var brushSize: Float = 40f

    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private val displayBrushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFF6600.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val displayEraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskBrushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskEraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskCanvas = Canvas(maskBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        maskBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y

        val displayPaint = if (activeTool == Tool.BRUSH) displayBrushPaint else displayEraserPaint
        val recordPaint  = if (activeTool == Tool.BRUSH) maskBrushPaint   else maskEraserPaint

        displayPaint.strokeWidth = brushSize
        recordPaint.strokeWidth  = brushSize

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                maskCanvas?.drawPoint(x, y, recordPaint)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                maskCanvas?.drawLine(lastX, lastY, x, y, recordPaint)
                lastX = x; lastY = y
                invalidate()
            }
        }
        return true
    }

    /**
     * Controlla se l'utente ha disegnato almeno qualcosa sulla maschera.
     * Campiona la bitmap ogni 4 pixel per motivi di prestazioni.
     *
     * @return `true` se è presente almeno un pixel opaco nella maschera
     */
    fun hasSelection(): Boolean {
        val bmp = maskBitmap ?: return false
        for (y in 0 until bmp.height step 4)
            for (x in 0 until bmp.width step 4)
                if ((bmp.getPixel(x, y) ushr 24) > 0) return true
        return false
    }

    /**
     * Restituisce una copia della maschera di selezione corrente,
     * oppure `null` se l'utente non ha disegnato nulla.
     *
     * @return bitmap ARGB_8888 con pixel bianchi opachi nell'area selezionata, oppure `null`
     */
    fun getSelectionMask(): Bitmap? {
        if (!hasSelection()) return null
        return maskBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Cancella completamente la maschera di selezione,
     * riportando il canvas allo stato iniziale trasparente.
     */
    fun clearSelection() {
        maskCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }
}
