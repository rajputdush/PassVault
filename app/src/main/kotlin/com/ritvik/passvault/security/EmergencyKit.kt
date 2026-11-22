package com.ritvik.passvault.security

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * A one-page PDF with the recovery key and plain instructions, meant to be printed and kept somewhere safe
 * (a home safe, a folder with other important papers). It contains the key in the clear, so the screen that
 * creates it tells the user to print it and delete the file.
 */
object EmergencyKit {
    private const val PAGE_WIDTH = 595 // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    fun create(recoveryKey: String, createdOn: String): ByteArray {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            draw(page.canvas, recoveryKey, createdOn)
            doc.finishPage(page)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun draw(canvas: Canvas, recoveryKey: String, createdOn: String) {
        val width = PAGE_WIDTH - 2 * MARGIN
        val blue = Color.rgb(0x3F, 0x6B, 0xFF)

        // Header band
        val band = Paint().apply { color = blue }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 100f, band)
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("PassVault Emergency Kit", MARGIN, 52f, title)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 13f
        }
        canvas.drawText("Created $createdOn", MARGIN, 76f, sub)

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x22, 0x22, 0x22)
            textSize = 12f
        }
        val heading = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x11, 0x11, 0x11)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 130f
        y = paragraph(canvas, "What this is", heading, y, width) + 4f
        y = paragraph(
            canvas,
            "PassVault keeps your passwords encrypted with your master password. If you ever forget that password, the recovery key below is the only way back into your vault. Nobody can reset it for you, because nobody else ever has your password or this key.",
            body, y, width,
        ) + 16f

        y = paragraph(canvas, "Your recovery key", heading, y, width) + 10f
        val groups = recoveryKey.split("-").filter { it.isNotEmpty() }.chunked(4).map { it.joinToString("-") }
        val keyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 19f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val lineHeight = 30f
        val boxHeight = groups.size * lineHeight + 28f
        val box = RectF(MARGIN, y, MARGIN + width, y + boxHeight)
        canvas.drawRoundRect(box, 12f, 12f, Paint().apply { color = Color.rgb(0xF1, 0xF3, 0xF8) })
        canvas.drawRoundRect(
            box, 12f, 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = Color.rgb(0xB8, 0xC0, 0xD6)
            },
        )
        groups.forEachIndexed { i, line ->
            canvas.drawText(line, MARGIN + 22f, y + 32f + i * lineHeight, keyPaint)
        }
        y += boxHeight + 20f

        y = paragraph(canvas, "Keep it safe", heading, y, width) + 4f
        y = paragraph(
            canvas,
            "- Print this page and keep it somewhere safe and private, such as a home safe. Do not keep it on the same phone as PassVault.\n- Then delete the PDF file from your phone, your downloads and any cloud storage or email you sent it through.\n- Anyone who has this key and a copy of your encrypted vault can open it. Treat it like a key to your house.\n- Do not photograph it or store it in a notes app.",
            body, y, width,
        ) + 16f

        y = paragraph(canvas, "If you forget your master password", heading, y, width) + 4f
        y = paragraph(
            canvas,
            "1. Open PassVault. On the lock screen tap \"Forgot password\".\n2. Type the recovery key above (capitals, small letters and dashes do not matter).\n3. Choose a new master password. Your passwords are still there.",
            body, y, width,
        ) + 16f

        y = paragraph(canvas, "Replaced this key?", heading, y, width) + 4f
        paragraph(
            canvas,
            "If you create a new recovery key in PassVault (Settings, Security), this one stops working. Destroy old copies so you never mix them up.",
            body, y, width,
        )

        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x88, 0x88, 0x88)
            textSize = 10f
        }
        canvas.drawText("PassVault Emergency Kit - contains a secret. Print, store safely, then delete the file.", MARGIN, PAGE_HEIGHT - 30f, foot)
    }

    /** Draws wrapped text starting at [y] and returns the y just below it. */
    private fun paragraph(canvas: Canvas, text: String, paint: TextPaint, y: Float, width: Float): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()
        canvas.save()
        canvas.translate(MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }
}
