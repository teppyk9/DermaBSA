package com.uninsubria.derma_bsa.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * View di disegno che si sovrappone all'ImageView dell'immagine ritagliata.
 *
 * La maschera viene registrata nelle coordinate della view (stesse coordinate
 * in cui l'ImageView sottostante mostra la foto con fitCenter), quindi i tratti
 * appaiono esattamente sopra i pixel corretti. In [getSelectionMask] la porzione
 * corrispondente alla zona foto (imageRect) viene estratta e scalata alle dimensioni
 * dell'immagine originale, pronta per essere passata a OnnxHelper.
 *
 * Il cursore circolare è visibile:
 * - durante il tocco (dito giù → si sposta con il dito)
 * - durante la regolazione dello slider (anteprima statica al centro per 1,5 s)
 */
class SelectionCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { BRUSH, ERASER }

    var activeTool: Tool = Tool.BRUSH

    /** Dimensione pennello/gomma in pixel-schermo. */
    var brushSize: Float = 40f
        set(value) {
            field = value
            cursorOuterPaint.strokeWidth = value.coerceAtLeast(6f) * 0.12f + 4f
        }

    // ── Bitmap maschera ──────────────────────────────────────────────────────
    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    // ── Info sull'immagine mostrata nell'ImageView sottostante ────────────────
    private var imgW = 0
    private var imgH = 0
    private val imageRect = RectF()   // dove fitCenter posiziona la foto nella view

    // ── Paint per il disegno sulla maschera ──────────────────────────────────
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FF6600.toInt()    // arancione ~60% opaco
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ── Paint cursore ─────────────────────────────────────────────────────────
    private val cursorOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0xCC000000.toInt()    // anello scuro per contrasto
    }
    private val cursorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    // ── Stato cursore ─────────────────────────────────────────────────────────
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorVisible = false

    private val hideCursorRunnable = Runnable {
        cursorVisible = false
        invalidate()
    }

    // ── Tratto precedente ─────────────────────────────────────────────────────
    private var lastX = 0f
    private var lastY = 0f

    // ─────────────────────────────────────────────────────────────────────────
    // API pubblica
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registra le dimensioni del bitmap mostrato dall'ImageView affiancato,
     * in modo da calcolare correttamente imageRect (fitCenter) e poter poi
     * estrarre la maschera nelle coordinate immagine.
     */
    fun setImageBitmap(bitmap: Bitmap) {
        imgW = bitmap.width
        imgH = bitmap.height
        computeImageRect()
    }

    /**
     * Mostra il cursore al centro della foto per ~1,5 s — chiamato dalla SeekBar
     * per far vedere in anteprima la dimensione del pennello/gomma.
     */
    fun previewCursor() {
        removeCallbacks(hideCursorRunnable)
        cursorX = if (imageRect.isEmpty) width / 2f else imageRect.centerX()
        cursorY = if (imageRect.isEmpty) height / 2f else imageRect.centerY()
        cursorVisible = true
        invalidate()
        postDelayed(hideCursorRunnable, 1500)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // Ricrea la bitmap maschera alle nuove dimensioni della view, preservando il contenuto.
        val old = maskBitmap
        maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskCanvas = Canvas(maskBitmap!!)
        old?.let { maskCanvas?.drawBitmap(it, 0f, 0f, null) }
        computeImageRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        maskBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (cursorVisible) {
            val r = brushSize / 2f
            canvas.drawCircle(cursorX, cursorY, r, cursorOuterPaint)
            canvas.drawCircle(cursorX, cursorY, r, cursorInnerPaint)
        }
    }

    /** Disegna o cancella sulla maschera seguendo il tratto del dito. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        cursorX = x
        cursorY = y

        val paint = if (activeTool == Tool.BRUSH) brushPaint else eraserPaint
        paint.strokeWidth = brushSize

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(hideCursorRunnable)
                cursorVisible = true
                lastX = x; lastY = y
                maskCanvas?.drawPoint(x, y, paint)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                maskCanvas?.drawLine(lastX, lastY, x, y, paint)
                lastX = x; lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cursorVisible = false
                invalidate()
            }
        }
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logica maschera
    // ─────────────────────────────────────────────────────────────────────────

    /** Controlla se l'utente ha dipinto almeno un pixel sulla maschera. */
    fun hasSelection(): Boolean {
        val bmp = maskBitmap ?: return false
        for (row in 0 until bmp.height step 4)
            for (col in 0 until bmp.width step 4)
                if ((bmp.getPixel(col, row) ushr 24) > 0) return true
        return false
    }

    /**
     * Ritorna la maschera di selezione ritagliata alla zona dell'immagine
     * (imageRect) e scalata alle dimensioni del bitmap originale.
     * Questo garantisce la corrispondenza pixel con quanto passa in OnnxHelper.
     */
    fun getSelectionMask(): Bitmap? {
        if (!hasSelection()) return null
        val bmp = maskBitmap ?: return null

        // Se non abbiamo ancora le info sull'immagine, restituiamo la maschera grezza.
        if (imgW <= 0 || imageRect.isEmpty) return bmp.copy(Bitmap.Config.ARGB_8888, false)

        val srcL = imageRect.left.toInt().coerceAtLeast(0)
        val srcT = imageRect.top.toInt().coerceAtLeast(0)
        val srcR = imageRect.right.toInt().coerceAtMost(bmp.width)
        val srcB = imageRect.bottom.toInt().coerceAtMost(bmp.height)
        val srcW = (srcR - srcL).coerceAtLeast(1)
        val srcH = (srcB - srcT).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(bmp, srcL, srcT, srcW, srcH)
        return Bitmap.createScaledBitmap(cropped, imgW, imgH, true)
    }

    /** Cancella tutta la selezione dipinta finora. */
    fun clearSelection() {
        maskCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility privata
    // ─────────────────────────────────────────────────────────────────────────

    /** Calcola dove l'ImageView fitCenter posiziona la foto all'interno di questa view. */
    private fun computeImageRect() {
        if (width == 0 || height == 0 || imgW == 0 || imgH == 0) return
        val scale = minOf(width.toFloat() / imgW, height.toFloat() / imgH)
        val dW = imgW * scale
        val dH = imgH * scale
        imageRect.set(
            (width - dW) / 2f, (height - dH) / 2f,
            (width + dW) / 2f, (height + dH) / 2f
        )
    }
}
