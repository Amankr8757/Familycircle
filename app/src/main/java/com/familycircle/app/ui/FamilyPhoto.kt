package com.familycircle.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64

internal fun decodeFamilyPhoto(photoBase64: String?): Bitmap? {
    if (photoBase64.isNullOrBlank()) return null
    return try {
        val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }
}

internal fun createMapAvatarBitmap(name: String, photoBase64: String?, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size * 2, size * 3 / 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = bitmap.width / 2f
    val centerY = size * 0.48f
    val radius = size * 0.43f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centerX, centerY, size * 0.47f, paint)
    val photo = decodeFamilyPhoto(photoBase64)
    if (photo != null) {
        val save = canvas.save()
        val path = android.graphics.Path().apply { addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        val scale = maxOf(size * 0.86f / photo.width, size * 0.86f / photo.height)
        val width = photo.width * scale
        val height = photo.height * scale
        val destination = RectF(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
        paint.color = android.graphics.Color.WHITE
        canvas.drawBitmap(photo, null, destination, paint)
        canvas.restoreToCount(save)
        photo.recycle()
    } else {
        paint.color = android.graphics.Color.rgb(91, 79, 224)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = size * 0.4f
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(name.take(1).uppercase(), centerX, baseline, paint)
    }
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 0.035f
    canvas.drawCircle(centerX, centerY, radius, paint)

    paint.style = Paint.Style.FILL
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = size * 0.23f
    val maxLabelWidth = size * 1.82f
    var label = name
    while (paint.measureText(label) > maxLabelWidth && label.length > 4) label = label.dropLast(2) + "…"
    val labelWidth = (paint.measureText(label) + size * 0.34f).coerceAtMost(size * 1.92f)
    val labelHeight = size * 0.34f
    val labelTop = size * 1.02f
    paint.color = android.graphics.Color.rgb(37, 38, 61)
    canvas.drawRoundRect(RectF(centerX - labelWidth / 2, labelTop, centerX + labelWidth / 2, labelTop + labelHeight), labelHeight / 2, labelHeight / 2, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = size * 0.19f
    val textBaseline = labelTop + labelHeight / 2 - (paint.ascent() + paint.descent()) / 2
    canvas.drawText(label, centerX, textBaseline, paint)
    return bitmap
}
