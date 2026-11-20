package org.kobostyle.launcher.ui

import android.graphics.*
import android.graphics.drawable.Drawable

/** Small monochrome, code-native icons; no bitmap assets, animation or vendor APIs. */
class ReaderIcon(private val kind: Kind, private val density: Float, private val active: Boolean = false) : Drawable() {
    enum class Kind { HOME, BOOKS, APPS, MORE, LIGHT, WIFI, REFRESH, SEARCH, CAT }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE
    }
    override fun getIntrinsicWidth() = (24 * density).toInt()
    override fun getIntrinsicHeight() = (24 * density).toInt()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = canvas.drawLine(x, y, x2, y2, ink)
        when (kind) {
            Kind.HOME -> {
                val path = Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f); lineTo(20f, 11f); lineTo(20f, 21f); lineTo(14f, 21f); lineTo(14f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(4f, 21f); lineTo(4f, 11f); close() }
                ink.style = if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                canvas.drawPath(path, ink); ink.style = Paint.Style.STROKE
            }
            Kind.BOOKS -> { canvas.drawRect(3f, 7f, 7f, 21f, ink); canvas.drawRect(9f, 3f, 13f, 21f, ink); val p = Path().apply { moveTo(15f, 8f); lineTo(19f, 7f); lineTo(23f, 20f); lineTo(19f, 21f); close() }; canvas.drawPath(p, ink) }
            Kind.APPS -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) canvas.drawRect(x, y, x + 6, y + 6, ink)
            Kind.CAT -> {
                val face = Path().apply { moveTo(4f, 9f); lineTo(4f, 2f); lineTo(9f, 6f); quadTo(12f, 5f, 15f, 6f); lineTo(20f, 2f); lineTo(20f, 9f); cubicTo(25f, 24f, -1f, 24f, 4f, 9f); close() }
                canvas.drawPath(face, ink); canvas.drawCircle(8f, 12f, 2.5f, ink); canvas.drawCircle(16f, 12f, 2.5f, ink)
                line(10.5f, 12f, 13.5f, 12f); line(11f, 16f, 13f, 16f)
            }
            Kind.MORE -> for (y in listOf(5f, 12f, 19f)) line(3f, y, 21f, y)
            Kind.LIGHT -> {
                canvas.drawCircle(12f, 12f, 4f, ink)
                for (i in 0..7) { val r = i * Math.PI / 4; line(12 + 7 * kotlin.math.cos(r).toFloat(), 12 + 7 * kotlin.math.sin(r).toFloat(), 12 + 10 * kotlin.math.cos(r).toFloat(), 12 + 10 * kotlin.math.sin(r).toFloat()) }
            }
            Kind.WIFI -> { canvas.drawArc(RectF(1f, 3f, 23f, 25f), 222f, 96f, false, ink); canvas.drawArc(RectF(5f, 8f, 19f, 22f), 222f, 96f, false, ink); canvas.drawArc(RectF(9f, 13f, 15f, 19f), 222f, 96f, false, ink); canvas.drawCircle(12f, 19f, .8f, ink) }
            Kind.REFRESH -> { canvas.drawArc(RectF(4f, 4f, 20f, 20f), 35f, 290f, false, ink); line(20f, 3f, 20f, 9f); line(20f, 9f, 14f, 9f) }
            Kind.SEARCH -> { canvas.drawCircle(10f, 10f, 6.5f, ink); line(15f, 15f, 22f, 22f) }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { ink.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { ink.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is unused on modern Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
