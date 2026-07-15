package com.badmintonledger.app.poster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.badmintonledger.domain.report.PosterAlign
import com.badmintonledger.domain.report.PosterLayout

/** Draws the domain-computed poster primitives 1:1 onto a bitmap (750px wide, like WeChat). */
fun renderPoster(layout: PosterLayout): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    layout.rects.forEach { r ->
        paint.color = Color.parseColor(r.color)
        canvas.drawRect(
            r.x.toFloat(),
            r.y.toFloat(),
            (r.x + r.w).toFloat(),
            (r.y + r.h).toFloat(),
            paint,
        )
    }
    layout.texts.forEach { t ->
        paint.color = Color.parseColor(t.color)
        paint.textSize = t.size.toFloat()
        paint.typeface = if (t.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign =
            when (t.align) {
                PosterAlign.LEFT -> Paint.Align.LEFT
                PosterAlign.CENTER -> Paint.Align.CENTER
                PosterAlign.RIGHT -> Paint.Align.RIGHT
            }
        canvas.drawText(t.text, t.x.toFloat(), t.y.toFloat(), paint)
    }
    return bitmap
}
