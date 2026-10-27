package com.zhuzhu.sudi.net

import android.content.Context
import com.zhuzhu.sudi.core.NameText
import com.zhuzhu.sudi.core.Record
import com.zhuzhu.sudi.core.StreamPump
import com.zhuzhu.sudi.store.Repository
import com.zhuzhu.sudi.store.Storage
import com.zhuzhu.sudi.ui.Palette
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * 电视端内嵌的上传服务。
 *
 * 协议刻意做得很窄：`PUT /upload?name=<文件名>` + 裸二进制 body。
 * 没用 multipart，是因为浏览器要用 multipart 就必须先把整个文件塞进一个 FormData，
 * 大文件会双份占用内存；一次一个文件、进度一条一条地走，反而更好用，也让服务端
 * 可以边收边写，不落临时文件。
 *
 * 同时服务手机页面（`GET /`）和最近文件列表。
 */
class UploadServer(
    private val context: Context,
    port: Int,
    private val repo: Repository,
    private val storage: Storage,
    private val palette: () -> Palette,
    private val brand: () -> String,
    private val page: () -> String,
    private val onUploaded: (Record) -> Unit,
    private val onProgress: (name: String, sent: Long, total: Long) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method

        return try {
            when {
                method == Method.GET && (uri == "/" || uri == "/index.html") -> html(page())
                method == Method.GET && uri == "/health" -> text(Response.Status.OK, "ok")
                method == Method.GET && uri == "/favicon.ico" ->
                    newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
                (method == Method.PUT || method == Method.POST) && uri == "/upload" -> upload(session)
                else -> text(Response.Status.NOT_FOUND, "没有这个地址")
            }
        } catch (e: Exception) {
            text(Response.Status.INTERNAL_ERROR, "服务器出错：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun upload(session: IHTTPSession): Response {
        val rawName = session.parameters["name"]?.firstOrNull()
            ?: session.headers["x-file-name"]
        val name = NameText.sanitize(rawName)
            ?: return text(Response.Status.BAD_REQUEST, "缺少文件名")

        val total = session.headers["content-length"]?.toLongOrNull() ?: -1L
        if (total == 0L) return text(Response.Status.BAD_REQUEST, "空文件")

        val finalName = repo.claimName(name)
        val target = try {
            storage.begin(finalName)
        } catch (e: Exception) {
            repo.releaseName(finalName)
            return text(Response.Status.INTERNAL_ERROR, "写入失败：${e.message ?: "无法创建文件"}")
        }
        // 落地的名字可能和认领的不一样（MediaStore / 目录里已有同名时会改成「xxx (2)」），
        // 一律以磁盘上的为准，否则列表名和文件管理器里看到的对不上。
        val actualName = target.name

        var lastReport = 0L
        return try {
            val sent = StreamPump.copy(session.inputStream, target.stream, total) { done ->
                val now = System.currentTimeMillis()
                if (now - lastReport > 120 || done == total) {
                    lastReport = now
                    onProgress(actualName, done, total)
                }
            }
            if (total > 0 && sent != total) {
                target.abort()
                return text(Response.Status.BAD_REQUEST, "传输不完整：收到 $sent / $total 字节")
            }
            target.commit()

            val record = Record(
                id = repo.nextId(),
                name = actualName,
                size = if (total > 0) total else sent,
                time = System.currentTimeMillis(),
                uri = target.uri,
                source = "手机"
            )
            repo.add(record)
            onUploaded(record)
            text(Response.Status.CREATED, "ok")
        } catch (e: IOException) {
            target.abort()
            text(Response.Status.INTERNAL_ERROR, "传输中断：${e.message ?: "连接被关闭"}")
        } finally {
            // 认领名一定要还：成功时 add() 释放的是磁盘上的实际名字，MediaStore 改过名时
            // 和认领名不同，不还的话这个名字会被永久占着，以后同名文件莫名变成「xxx (2)」。
            repo.releaseName(finalName)
        }
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun text(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    companion object {
        /** 端口被占用时依次往后试，返回实际用上的端口。 */
        fun startOnFreePort(
            context: Context,
            port: Int,
            repo: Repository,
            storage: Storage,
            palette: () -> Palette,
            brand: () -> String,
            page: () -> String,
            onUploaded: (Record) -> Unit,
            onProgress: (String, Long, Long) -> Unit
        ): UploadServer? {
            for (candidate in port until port + 12) {
                val server = UploadServer(
                    context, candidate, repo, storage, palette, brand, page, onUploaded, onProgress
                )
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: IOException) {
                    runCatching { server.stop() }
                }
            }
            return null
        }
    }
}
