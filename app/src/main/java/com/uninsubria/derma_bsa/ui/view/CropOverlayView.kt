package com.uninsubria.derma_bsa.ui.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.uninsubria.derma_bsa.R
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * View personalizzata che mostra una foto con sovrapposta la sagoma SVG del
 * distretto anatomico selezionato. L'utente può spostare, ridimensionare e
 * ruotare la sagoma tramite gesture touch, poi confermare il ritaglio.
 *
 * Gesture supportate:
 * - 1 dito: traslazione della sagoma
 * - 2 dita: ridimensionamento e rotazione della sagoma
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var regionId: String = "torso_front"
        set(value) {
            field = value
            overlayBitmap = renderDrawable(value)
            overlayInitialized = false
            if (width > 0 && height > 0) resetOverlay()
            invalidate()
        }

    var image: Bitmap? = null
        set(value) {
            field = value
            computeImageRect()
            overlayInitialized = false
            resetOverlay()
            invalidate()
        }

    private var overlayBitmap: Bitmap? = null
    private val overlayMatrix = Matrix()
    private var imageRect = RectF()
    private var overlayInitialized = false

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 180
        colorFilter = PorterDuffColorFilter(0xFF1565C0.toInt(), PorterDuff.Mode.SRC_ATOP)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }

    private var p0x = 0f; private var p0y = 0f
    private var p1x = 0f; private var p1y = 0f
    private var activePointers = 0

    init {
        overlayBitmap = renderDrawable("torso_front")
    }

    /** Rende il VectorDrawable corrispondente al regionId in un Bitmap trasparente. */
    private fun renderDrawable(regionId: String): Bitmap? {
        val resId = drawableForRegion(regionId) ?: return null
        val drawable = ContextCompat.getDrawable(context, resId) as? VectorDrawable ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 80
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 80
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    /** Mappa il regionId al drawable resource corrispondente. */
    private fun drawableForRegion(id: String): Int? = when {
        id.startsWith("head")             -> R.drawable.bsa_head
        id.startsWith("neck")             -> R.drawable.bsa_neck
        id == "torso_front"               -> R.drawable.bsa_torso_front
        id == "torso_back"                -> R.drawable.bsa_torso_back
        id == "groin"                     -> R.drawable.bsa_groin
        id == "gluteus_left"              -> R.drawable.bsa_gluteus_left
        id == "gluteus_right"             -> R.drawable.bsa_gluteus_right
        id.startsWith("upper_arm_left")   -> R.drawable.bsa_upper_arm_left
        id.startsWith("upper_arm_right")  -> R.drawable.bsa_upper_arm_right
        id.startsWith("forearm_left")     -> R.drawable.bsa_forearm_left
        id.startsWith("forearm_right")    -> R.drawable.bsa_forearm_right
        id.startsWith("hand_left")        -> R.drawable.bsa_hand_left
        id.startsWith("hand_right")       -> R.drawable.bsa_hand_right
        id.startsWith("thigh_left")       -> R.drawable.bsa_thigh_left
        id.startsWith("thigh_right")      -> R.drawable.bsa_thigh_right
        id.startsWith("lower_leg_left")   -> R.drawable.bsa_lower_leg_left
        id.startsWith("lower_leg_right")  -> R.drawable.bsa_lower_leg_right
        id.startsWith("foot_left")        -> R.drawable.bsa_foot_left
        id.startsWith("foot_right")       -> R.drawable.bsa_foot_right
        else                              -> null
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        computeImageRect()
        if (!overlayInitialized) resetOverlay()
    }

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

    /** Posiziona la sagoma al centro dell'immagine scalata in modo che
     *  occupi circa il 55% del lato più corto del rettangolo immagine. */
    private fun resetOverlay() {
        val bmp = overlayBitmap ?: return
        if (width == 0 || height == 0) return
        overlayMatrix.reset()
        val refRect = if (imageRect.isEmpty) RectF(0f, 0f, width.toFloat(), height.toFloat()) else imageRect

        val scaleX = refRect.width()  * 0.55f / bmp.width
        val scaleY = refRect.height() * 0.55f / bmp.height
        val s = minOf(scaleX, scaleY)

        val cx = refRect.centerX() - bmp.width  * s / 2f
        val cy = refRect.centerY() - bmp.height * s / 2f
        overlayMatrix.postScale(s, s)
        overlayMatrix.postTranslate(cx, cy)
        overlayInitialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        image?.let { canvas.drawBitmap(it, null, imageRect, imagePaint) }

        overlayBitmap?.let { bmp ->
            canvas.save()
            canvas.concat(overlayMatrix)
            canvas.drawBitmap(bmp, 0f, 0f, overlayPaint)
            canvas.restore()
        }

        if (image != null) {
            canvas.drawText("Posiziona e ridimensiona la sagoma", width / 2f, height - 24f, hintPaint)
        }
    }

    /** Gestisce pan con un dito e zoom+rotazione con due dita sulla silhouette. */
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
            MotionEvent.ACTION_MOVE -> { handleMove(event); invalidate() }
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
     * Ritaglia l'immagine nella forma e posizione corrente della sagoma SVG.
     *
     * La sagoma trasformata viene usata come maschera alpha (DST_IN) per
     * ritagliare la foto. Restituisce anche l'overlapRatio (frazione della
     * sagoma che cade dentro i bordi della foto).
     */
    fun cropImage(): Pair<Bitmap, Float>? {
        val img = image ?: return null
        val bmp = overlayBitmap ?: return null
        if (imageRect.isEmpty) return null

        val imageToView = Matrix().apply {
            setRectToRect(
                RectF(0f, 0f, img.width.toFloat(), img.height.toFloat()),
                imageRect,
                Matrix.ScaleToFit.FILL
            )
        }
        val viewToImage = Matrix().also { imageToView.invert(it) }

        val bmpToImage = Matrix(overlayMatrix).apply { postConcat(viewToImage) }

        val corners = floatArrayOf(
            0f, 0f,
            bmp.width.toFloat(), 0f,
            bmp.width.toFloat(), bmp.height.toFloat(),
            0f, bmp.height.toFloat()
        )
        bmpToImage.mapPoints(corners)

        val minX = minOf(corners[0], corners[2], corners[4], corners[6])
        val maxX = maxOf(corners[0], corners[2], corners[4], corners[6])
        val minY = minOf(corners[1], corners[3], corners[5], corners[7])
        val maxY = maxOf(corners[1], corners[3], corners[5], corners[7])
        val fullBounds = RectF(minX, minY, maxX, maxY)

        val clipped = RectF(fullBounds)
        val hasOverlap = clipped.intersect(0f, 0f, img.width.toFloat(), img.height.toFloat())
        if (!hasOverlap || clipped.isEmpty) return null

        val fullArea    = fullBounds.width() * fullBounds.height()
        val clippedArea = clipped.width()    * clipped.height()
        val overlapRatio = if (fullArea > 0f) (clippedArea / fullArea).coerceIn(0f, 1f) else 1f

        val cropW = clipped.width().toInt().coerceAtLeast(1)
        val cropH = clipped.height().toInt().coerceAtLeast(1)

        val photoCrop = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        Canvas(photoCrop).apply {
            translate(-clipped.left, -clipped.top)
            drawBitmap(img, 0f, 0f, null)
        }

        val maskBmp = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val maskMatrix = Matrix(bmpToImage).apply {
            postTranslate(-clipped.left, -clipped.top)
        }
        Canvas(maskBmp).drawBitmap(bmp, maskMatrix, Paint(Paint.ANTI_ALIAS_FLAG))

        val result = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        resultCanvas.drawBitmap(photoCrop, 0f, 0f, null)
        resultCanvas.drawBitmap(
            maskBmp, 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        )

        return Pair(result, overlapRatio)
    }
}
