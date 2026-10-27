package com.zhuzhu.sudi.core

import java.io.InputStream
import java.io.OutputStream

/**
 * 上传数据的搬运工。
 *
 * 两头都是流：手机那头是 socket，电视这头是 MediaStore 的输出流。
 * 中间不落地临时文件、不整包进内存——电视盒子内存通常只有 1~2 GB，
 * 传一部 4 GB 的电影时任何「先存临时文件再搬」的做法都会把盒子拖死。
 */
object StreamPump {

    private const val BUFFER = 64 * 1024

    /**
     * @param limit 最多搬运多少字节；-1 表示读到 EOF。
     * @return 实际搬运的字节数。
     */
    fun copy(
        input: InputStream,
        output: OutputStream,
        limit: Long = -1L,
        onProgress: ((Long) -> Unit)? = null
    ): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val remaining = if (limit >= 0) limit - total else Long.MAX_VALUE
            if (remaining <= 0L) break
            val want = if (remaining < BUFFER) remaining.toInt() else BUFFER
            val read = input.read(buffer, 0, want)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
            onProgress?.invoke(total)
        }
        return total
    }
}
