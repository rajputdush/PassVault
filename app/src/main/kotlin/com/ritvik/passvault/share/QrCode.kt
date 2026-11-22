package com.ritvik.passvault.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code drawn with the Canvas, so no image file or camera permission is involved. It is always black on white,
 * even in dark mode, because scanners need that contrast.
 */
@Composable
fun QrCode(text: String, size: Dp = 240.dp) {
    val matrix: BitMatrix? = remember(text) {
        try {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: WriterException) {
            null
        }
    }
    Canvas(modifier = Modifier.size(size).background(Color.White)) {
        val m = matrix ?: return@Canvas
        val quiet = 4 // the blank border a scanner needs, in modules
        val modules = m.width + quiet * 2
        val cell = this.size.minDimension / modules
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                        size = Size(cell + 0.5f, cell + 0.5f), // a hair of overlap so no seams show between modules
                    )
                }
            }
        }
    }
}
