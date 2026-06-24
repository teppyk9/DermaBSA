package com.uninsubria.derma_bsa.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.uninsubria.derma_bsa.model.ALL_REGIONS
import com.uninsubria.derma_bsa.model.BodyRegion

/**
 * View personalizzata che disegna una mappa anatomica stilizzata del corpo umano
 * secondo la Regola dei Nove, con i distretti anatomici toccabili.
 *
 * Può mostrare la vista frontale o posteriore tramite la proprietà [retro].
 * Quando l'utente tocca un distretto viene invocata la callback [onRegionSelected].
 * Il distretto attualmente selezionato viene evidenziato con un colore diverso.
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /**
     * Se `true` mostra la vista posteriore del corpo (schiena),
     * altrimenti mostra la vista frontale. Modificare questa proprietà
     * deseleziona il distretto corrente e ridisegna la View.
     */
    var retro: Boolean = false
        set(value) { field = value; selected = null; invalidate() }

    /** Distretto attualmente selezionato, oppure `null` se nessuno è stato toccato. */
    var selected: BodyRegion? = null
        set(value) { field = value; invalidate() }

    /** Callback invocata ogni volta che l'utente tocca un distretto. */
    var onRegionSelected: ((BodyRegion) -> Unit)? = null

    private val paintFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintText   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
    }

    private fun regioniVisibili(): List<BodyRegion> {
        val ids = buildList {
            add("head"); add("arm_left"); add("arm_right")
            add(if (retro) "trunk_back" else "trunk_front")
            if (!retro) add("genitals")
            add("leg_left"); add("leg_right")
        }
        return ids.mapNotNull { id -> ALL_REGIONS.firstOrNull { it.id == id } }
    }

    /**
     * Costruisce il [Path] di un distretto scalato alle dimensioni [w]×[h] della View.
     * Le coordinate sono definite come frazioni della larghezza e dell'altezza.
     *
     * @param id identificatore del distretto
     * @param w larghezza della View in pixel
     * @param h altezza della View in pixel
     * @return path pronto per essere disegnato su canvas
     */
    private fun pathDi(id: String, w: Float, h: Float): Path {
        fun rrect(l: Float, t: Float, r: Float, b: Float, cr: Float = 14f) = Path().apply {
            addRoundRect(RectF(l, t, r, b), cr, cr, Path.Direction.CW)
        }
        return when (id) {
            "head"                    -> Path().apply {
                addOval(RectF(0.41f * w, 0.04f * h, 0.59f * w, 0.17f * h), Path.Direction.CW)
            }
            "trunk_front", "trunk_back" -> rrect(0.38f * w, 0.18f * h, 0.62f * w, 0.50f * h)
            "arm_left"                -> rrect(0.27f * w, 0.19f * h, 0.37f * w, 0.52f * h, 10f)
            "arm_right"               -> rrect(0.63f * w, 0.19f * h, 0.73f * w, 0.52f * h, 10f)
            "genitals"                -> rrect(0.44f * w, 0.50f * h, 0.56f * w, 0.56f * h, 8f)
            "leg_left"                -> rrect(0.40f * w, 0.52f * h, 0.49f * w, 0.94f * h, 10f)
            "leg_right"               -> rrect(0.51f * w, 0.52f * h, 0.60f * w, 0.94f * h, 10f)
            else                      -> Path()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paintText.textSize = h * 0.022f
        val bounds = RectF()

        for (r in regioniVisibili()) {
            val p = pathDi(r.id, w, h)
            val selezionato = r.id == selected?.id

            paintFill.color   = if (selezionato) 0x551565C0 else 0x33607D8B
            paintStroke.color = if (selezionato) Color.parseColor("#1565C0") else Color.parseColor("#90A4AE")
            paintStroke.strokeWidth = if (selezionato) 6f else 2f

            canvas.drawPath(p, paintFill)
            canvas.drawPath(p, paintStroke)

            p.computeBounds(bounds, true)
            canvas.drawText(
                "${r.bsaPercent.toInt()}%",
                bounds.centerX(),
                bounds.centerY() + paintText.textSize / 3f,
                paintText
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)

        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return true

        val clip = Region(0, 0, width, height)
        for (r in regioniVisibili()) {
            val region = Region()
            region.setPath(pathDi(r.id, w, h), clip)
            if (region.contains(event.x.toInt(), event.y.toInt())) {
                selected = r
                onRegionSelected?.invoke(r)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
