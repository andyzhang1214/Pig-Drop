package com.zhuzhu.sudi.core

/**
 * 文件名净化。
 *
 * 手机端传上来的名字是**不可信输入**：可能带路径分隔符（`../../etc/passwd`）、
 * 控制字符、超长中文名、或者干脆为空。这里做三件事：
 * 1. 只保留最后一段文件名，掐掉任何目录穿越；
 * 2. 去掉控制字符与两侧空白，隐藏文件（前导点）也去掉，电视上没人想看见 `.` 开头的文件；
 * 3. 限长时保住扩展名——截断把 `.mp4` 切掉，播放器就认不出来了。
 */
object NameText {

    private const val MAX_LENGTH = 100

    fun sanitize(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null

        var name = raw.substringAfterLast('/').substringAfterLast('\\')
        name = name.filter { !it.isISOControl() }.trim()
        name = name.trimStart('.')
        name = name.replace(Regex("\\s+"), " ")

        if (name.isEmpty() || name == "." || name == "..") return null
        return clamp(name)
    }

    /** 同名时追加「(2)」「(3)」…，扩展名保持在最后。 */
    fun unique(name: String, existing: Collection<String>): String {
        val taken = existing.map { it.lowercase() }.toHashSet()
        if (name.lowercase() !in taken) return name

        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""

        var index = 2
        while (true) {
            val candidate = clamp("$base ($index)$ext")
            if (candidate.lowercase() !in taken) return candidate
            index++
            if (index > 9999) return clamp("$base-${System.currentTimeMillis()}$ext")
        }
    }

    /** 截断但保住扩展名，结果不超过 [MAX_LENGTH] 个字符。 */
    private fun clamp(name: String): String {
        if (name.length <= MAX_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 12) name.substring(dot) else ""
        val keep = MAX_LENGTH - ext.length
        return name.substring(0, keep.coerceAtLeast(1)) + ext
    }
}
