package com.uninsubria.derma_bsa.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * View personalizzata che mostra una foto con sovrapposta la sagoma del distretto
 * anatomico selezionato. L'utente può spostare, ridimensionare e ruotare la sagoma
 * tramite gesture touch, poi confermare il ritaglio.
 *
 * Gesture supportate:
 * - 1 dito: traslazione della sagoma
 * - 2 dita: ridimensionamento e rotazione della sagoma
 *
 * Chiamare [cropImage] per ottenere la porzione di immagine ritagliata
 * nella forma della sagoma nella sua posizione corrente.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * Identificatore del distretto anatomico da mostrare come sagoma.
     * Cambiare questa proprietà ridisegna immediatamente la sagoma.
     */
    var regionId: String = "trunk_front"
        set(value) { field = value; basePath.set(buildBasePath(value)); invalidate() }

    /**
     * Immagine da mostrare come sfondo su cui posizionare la sagoma.
     * Cambiare questa proprietà ricalcola la posizione iniziale della sagoma.
     */
    var image: Bitmap? = null
        set(value) { field = value; computeImageRect(); resetOverlay(); invalidate() }

    private val basePath = Path()
    private val overlayMatrix = Matrix()
    private var imageRect = RectF()
    private var overlayInitialized = false

    private val imagePaint  = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x550000FF
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC2979FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val hintPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }

    private var p0x = 0f; private var p0y = 0f
    private var p1x = 0f; private var p1y = 0f
    private var activePointers = 0

    init {
        basePath.set(buildBasePath("trunk_front"))
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        computeImageRect()
        if (!overlayInitialized) resetOverlay()
    }

    /**
     * Calcola il rettangolo all'interno della View in cui viene disegnata l'immagine,
     * mantenendo le proporzioni originali (letterbox/pillarbox).
     */
    private fun computeImageRect() {
        val img = image ?: return
        if (width == 0 || height == 0) return
        val imgAspect  = img.width.toFloat() / img.height
        val viewAspect = width.toFloat() / height
        imageRect = if (imgAspect > viewAspect) {
            val scale = width.toFloat() / img.width
            val dH = img.height * scale
            RectF(0f, (height - dH) / 2f, width.toFloat(), (height + dH) / 2f)
        } else {
            val scale = height.toFloat() / img.height
            val dW = img.width * scale
            RectF((width - dW) / 2f, 0f, (width + dW) / 2f, height.toFloat())
        }
    }

    /**
     * Posiziona la sagoma al centro dell'immagine con una dimensione iniziale pari
     * al 45% del lato più corto del rettangolo immagine.
     */
    private fun resetOverlay() {
        if (width == 0 || height == 0) return
        overlayMatrix.reset()
        val refRect = if (imageRect.isEmpty) RectF(0f, 0f, width.toFloat(), height.toFloat()) else imageRect
        val s  = minOf(refRect.width(), refRect.height()) * 0.45f
        val cx = refRect.centerX()
        val cy = refRect.centerY()
        overlayMatrix.postScale(s, s)
        overlayMatrix.postTranslate(cx, cy)
        overlayInitialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        image?.let { canvas.drawBitmap(it, null, imageRect, imagePaint) }

        val rendered = Path()
        basePath.transform(overlayMatrix, rendered)
        canvas.drawPath(rendered, fillPaint)
        canvas.drawPath(rendered, strokePaint)

        if (image != null) {
            canvas.drawText("Posiziona e ridimensiona la sagoma", width / 2f, height - 24f, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                p0x = event.getX(0); p0y = event.getY(0)
                activePointers = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                p0x = event.getX(0); p0y = event.getY(0)
                p1x = event.getX(1); p1y = event.getY(1)
                activePointers = 2
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                activePointers = 1
                val remaining = if (event.actionIndex == 0) 1 else 0
                p0x = event.getX(remaining); p0y = event.getY(remaining)
            }
            MotionEvent.ACTION_UP -> activePointers = 0
        }
        return true
    }

    private fun handleMove(event: MotionEvent) {
        if (activePointers == 1) {
            overlayMatrix.postTranslate(event.getX(0) - p0x, event.getY(0) - p0y)
            p0x = event.getX(0); p0y = event.getY(0)
        } else if (activePointers >= 2 && event.pointerCount >= 2) {
            val nx0 = event.getX(0); val ny0 = event.getY(0)
            val nx1 = event.getX(1); val ny1 = event.getY(1)

            val oldSpan = hypot((p1x - p0x).toDouble(), (p1y - p0y).toDouble()).toFloat()
            val newSpan = hypot((nx1 - nx0).toDouble(), (ny1 - ny0).toDouble()).toFloat()
            val scale   = if (oldSpan > 1f) newSpan / oldSpan else 1f

            val oldAngle = atan2((p1y - p0y).toDouble(), (p1x - p0x).toDouble())
            val newAngle = atan2((ny1 - ny0).toDouble(), (nx1 - nx0).toDouble())
            val rotation = Math.toDegrees(newAngle - oldAngle).toFloat()

            val fxOld = (p0x + p1x) / 2f; val fyOld = (p0y + p1y) / 2f
            val fxNew = (nx0 + nx1)  / 2f; val fyNew = (ny0 + ny1)  / 2f

            overlayMatrix.postScale(scale, scale, fxNew, fyNew)
            overlayMatrix.postRotate(rotation, fxNew, fyNew)
            overlayMatrix.postTranslate(fxNew - fxOld, fyNew - fyOld)

            p0x = nx0; p0y = ny0; p1x = nx1; p1y = ny1
        }
    }

    /**
     * Ritaglia l'immagine nella forma e nella posizione corrente della sagoma.
     *
     * Mappa il path dal sistema di coordinate della View a quello dell'immagine originale,
     * applica il path come clip e disegna l'immagine su un nuovo Bitmap.
     *
     * @return il bitmap ritagliato, oppure `null` se non c'è un'immagine caricata
     */
    fun cropImage(): Bitmap? {
        val img = image ?: return null
        if (imageRect.isEmpty) return null

        val imageToView = Matrix()
        imageToView.setRectToRect(
            RectF(0f, 0f, img.width.toFloat(), img.height.toFloat()),
            imageRect,
            Matrix.ScaleToFit.FILL
        )
        val viewToImage = Matrix()
        imageToView.invert(viewToImage)

        val viewPath = Path()
        basePath.transform(overlayMatrix, viewPath)
        val imagePath = Path()
        viewPath.transform(viewToImage, imagePath)

        val bounds = RectF()
        imagePath.computeBounds(bounds, true)
        bounds.intersect(0f, 0f, img.width.toFloat(), img.height.toFloat())

        val cropW  = bounds.width().toInt().coerceAtLeast(1)
        val cropH  = bounds.height().toInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.clipPath(imagePath)
        canvas.drawBitmap(img, 0f, 0f, null)
        return result
    }

    /**
     * Restituisce il path base della sagoma nel sistema di coordinate normalizzato
     * (asse -0.5..0.5), con le proporzioni anatomiche del distretto specificato.
     *
     * @param regionId identificatore del distretto
     * @return path normalizzato della sagoma
     */
    private fun buildBasePath(regionId: String): Path = when (regionId) {
        "head"                      -> Path().apply {
            addOval(RectF(-0.5f, -0.36f, 0.5f, 0.36f), Path.Direction.CW)
        }
        "trunk_front", "trunk_back" -> Path().apply {
            addRoundRect(RectF(-0.375f, -0.5f, 0.375f, 0.5f), 0.08f, 0.08f, Path.Direction.CW)
        }
        "arm_left", "arm_right"     -> Path().apply {
            addRoundRect(RectF(-0.15f, -0.5f, 0.15f, 0.5f), 0.08f, 0.08f, Path.Direction.CW)
        }
        "genitals"                  -> Path().apply {
            addRoundRect(RectF(-0.5f, -0.25f, 0.5f, 0.25f), 0.1f, 0.1f, Path.Direction.CW)
        }
        "leg_left", "leg_right"     -> Path().apply {
            addRoundRect(RectF(-0.105f, -0.5f, 0.105f, 0.5f), 0.06f, 0.06f, Path.Direction.CW)
        }
        else                        -> Path().apply {
            addRoundRect(RectF(-0.5f, -0.5f, 0.5f, 0.5f), 0.1f, 0.1f, Path.Direction.CW)
        }
    }
}
