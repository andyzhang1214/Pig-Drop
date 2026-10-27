package com.zhuzhu.sudi.net

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码绘制。
 *
 * 只做矩阵 → 位图这一步（ZXing 负责编码）。必须衬浅底、深色码点，
 * 深色主题下也别偷懒用透明底——电视画面本来就暗，扫不出来才是最要命的失败。
 */
object QrBitmap {

    fun render(text: String, sizePx: Int, fg: Int, bg: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // 四周留一格空白：贴着边框的二维码手机很难定位
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun render(text: String, sizePx: Int): Bitmap = render(text, sizePx, Color.BLACK, Color.WHITE)
}
