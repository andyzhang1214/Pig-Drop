package com.zhuzhu.sudi.core

/**
 * 手机页面的模板填充。
 *
 * 页面本身是 assets 里的静态 HTML，服务器只做占位符替换——
 * 这样改版式不用碰 Kotlin，改占位符不会破坏 Kotlin，两边各管各的。
 * 替换必须一次性完成（不能反复 replace 到字符串里再被下一次替换命中）。
 */
object PageTemplate {

    fun render(template: String, vars: Map<String, String>): String {
        if (vars.isEmpty()) return template
        val sb = StringBuilder(template.length + 256)
        var i = 0
        while (i < template.length) {
            val start = template.indexOf("{{", i)
            if (start < 0) {
                sb.append(template, i, template.length)
                break
            }
            val end = template.indexOf("}}", start + 2)
            if (end < 0) {
                sb.append(template, i, template.length)
                break
            }
            sb.append(template, i, start)
            val key = template.substring(start + 2, end)
            val value = vars[key]
            // 没定义的占位符原样保留，方便在浏览器里一眼看出漏了哪个
            sb.append(value ?: template.substring(start, end + 2))
            i = end + 2
        }
        return sb.toString()
    }

    /** 模板里的文本插入点统一走这里，避免文件名把页面结构撑破。 */
    fun escape(text: String): String = buildString(text.length + 16) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
