package com.zhuzhu.sudi.net

import android.content.Context
import com.zhuzhu.sudi.core.PageTemplate
import com.zhuzhu.sudi.core.Record
import com.zhuzhu.sudi.core.SizeText
import com.zhuzhu.sudi.core.TimeText
import com.zhuzhu.sudi.ui.Palette

/**
 * 手机页面渲染。
 *
 * 版式在 assets/www/index.html 里，这里只负责把品牌、地址、主题变量和「最近送达」
 * 塞进占位符。手机端也就跟着电视的主题走——在电视上换了主题，扫码打开的手机页
 * 立刻是同一套颜色，这种「同一个产品」的感觉比省事更重要。
 */
object PhonePage {

    fun build(
        context: Context,
        baseUrl: String,
        palette: Palette,
        brand: String,
        records: List<Record>,
        now: Long = System.currentTimeMillis()
    ): String {
        val template = runCatching {
            context.assets.open("www/index.html").bufferedReader().use { it.readText() }
        }.getOrElse { fallbackHtml() }

        return PageTemplate.render(
            template,
            mapOf(
                "BRAND" to PageTemplate.escape(brand),
                "URL" to PageTemplate.escape(baseUrl),
                "CSS" to css(palette),
                "RECENT" to recentHtml(records, now),
                "RECENT_META" to PageTemplate.escape(recentMeta(records))
            )
        )
    }

    private fun css(p: Palette): String = """
        :root{
          --bg:#%s;--bg2:#%s;--surface:#%s;--surface2:#%s;
          --ink:#%s;--ink2:#%s;--ink3:#%s;
          --accent:#%s;--accent2:#%s;--accent-ink:#%s;--danger:#%s;
          --line:#%s;--line2:#%s;--radius:%dpx;
        }
    """.trimIndent().format(
        hex(p.bg), hex(p.bg2), hex(p.surface), hex(p.surface2),
        hex(p.ink), hex(p.ink2), hex(p.ink3),
        hex(p.accent), hex(p.accent2), hex(p.accentInk), hex(p.danger),
        hex(p.line), hex(p.line2), p.radiusDp.toInt()
    )

    private fun hex(color: Int): String = String.format("%06X", color and 0xFFFFFF)

    private fun recentMeta(records: List<Record>): String {
        if (records.isEmpty()) return "还没有收到文件"
        return "${records.size} 个文件 · ${SizeText.totalOf(records.sumOf { it.size })}"
    }

    private fun recentHtml(records: List<Record>, now: Long): String {
        if (records.isEmpty()) {
            return """<li class="rec-empty">电视上还没有文件，传一个试试</li>"""
        }
        return records.take(6).joinToString("") { record ->
            """<li class="rec"><span class="rec-name">${PageTemplate.escape(record.name)}</span>
               <span class="rec-meta">${SizeText.of(record.size)} · ${PageTemplate.escape(TimeText.ago(record.time, now))}</span></li>"""
        }
    }

    /**
     * assets 被裁掉时的兜底页面。
     *
     * 这里曾经挂过一个 `multipart/form-data` 的 form，但服务端只认 `PUT` 加裸 body，
     * 那个表单按下去必然 405——页面在，功能是假的。资源缺失说明安装包本身就不完整，
     * 与其给一个骗人的上传框，不如直接说清楚该怎么办。
     */
    private fun fallbackHtml(): String = """
        <!doctype html><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>{{BRAND}}</title>
        <style>body{font-family:system-ui;background:#070D14;color:#F0F4F9;padding:24px;line-height:1.7}</style>
        <h1>{{BRAND}}</h1>
        <p>电视端页面资源缺失，这个安装包不完整，请重新安装应用后再扫码。</p>
        <p>电视地址：{{URL}}</p>
    """.trimIndent()
}
