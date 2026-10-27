package com.zhuzhu.sudi.core

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 索引文件的编解码。
 *
 * 为什么不用 JSON：这个索引要在单元测试里跑（JVM 上没有 `org.json`），
 * 内容又只有「一行一条记录」这么简单，用制表符分隔 + 文件名百分号转义足够，
 * 还能直接 `cat` 出来排错。
 */
object IndexCodec {

    private const val HEADER = "#zz1"
    private const val SEP = '\t'

    fun encode(records: List<Record>): String {
        val sb = StringBuilder(HEADER).append('\n')
        for (r in records) {
            sb.append(r.id).append(SEP)
                .append(URLEncoder.encode(r.name, "UTF-8")).append(SEP)
                .append(r.size).append(SEP)
                .append(r.time).append(SEP)
                .append(r.uri).append(SEP)
                .append(URLEncoder.encode(r.source, "UTF-8")).append('\n')
        }
        return sb.toString()
    }

    /** 坏行直接跳过——索引损坏不该让整个列表变成空。 */
    fun decode(text: String): List<Record> {
        val out = ArrayList<Record>()
        if (text.isBlank()) return out
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split(SEP)
            if (parts.size < 5) continue
            val id = parts[0].toLongOrNull() ?: continue
            val size = parts[2].toLongOrNull() ?: continue
            val time = parts[3].toLongOrNull() ?: continue
            val name = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull() ?: continue
            val source = if (parts.size > 5) {
                runCatching { URLDecoder.decode(parts[5], "UTF-8") }.getOrDefault("手机")
            } else "手机"
            out.add(Record(id, name, size, time, parts[4], source))
        }
        return out
    }
}
