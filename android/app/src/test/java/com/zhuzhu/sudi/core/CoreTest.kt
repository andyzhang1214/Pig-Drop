package com.zhuzhu.sudi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CoreTest {

    // ---------- 体积 ----------

    @Test
    fun size_bytes_below_one_kb_stays_raw() {
        assertEquals("0 B", SizeText.of(0))
        assertEquals("812 B", SizeText.of(812))
        assertEquals("1023 B", SizeText.of(1023))
    }

    @Test
    fun size_keeps_one_decimal_only_under_ten() {
        assertEquals("1.0 KB", SizeText.of(1024))
        assertEquals("6.4 MB", SizeText.of((6.4 * 1048576).toLong()))
        assertEquals("428 MB", SizeText.of(428L * 1048576))
        assertEquals("1.2 GB", SizeText.of(1200L * 1048576))
    }

    @Test
    fun size_never_prints_ten_point_zero() {
        // 9.97 MB 四舍五入会变成 10.0，这里必须退成 10
        assertEquals("10 MB", SizeText.of(10450000))
    }

    // ---------- 相对时间 ----------

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun time_fresh_and_minutes() {
        val now = at(2026, 10, 27, 14, 0)
        assertEquals("刚刚", TimeText.ago(now - 5_000, now))
        assertEquals("12 分钟前", TimeText.ago(now - 12 * 60_000L, now))
    }

    @Test
    fun time_hours_within_same_day() {
        val now = at(2026, 10, 27, 14, 0)
        assertEquals("2 小时前", TimeText.ago(now - 2 * 3_600_000L, now))
        assertEquals("3 小时前", TimeText.ago(now - 200 * 60_000L, now))
    }

    @Test
    fun time_under_24h_says_hours_even_across_midnight() {
        // 昨天 23:50 传的文件，今天 00:10 看：才过 20 分钟，「20 分钟前」比「昨天」有用
        val now = at(2026, 10, 27, 0, 10)
        val then = at(2026, 10, 26, 23, 50)
        assertEquals("20 分钟前", TimeText.ago(then, now))
    }

    @Test
    fun time_over_24h_says_yesterday() {
        // 昨天 09:00 到今天 14:00 共 29 小时，此时「昨天」才是对的
        val now = at(2026, 10, 27, 14, 0)
        assertEquals("昨天", TimeText.ago(at(2026, 10, 26, 9, 0), now))
    }

    @Test
    fun time_days_then_date() {
        val now = at(2026, 10, 27, 14, 0)
        assertEquals("2 天前", TimeText.ago(at(2026, 10, 25, 9, 0), now))
        assertEquals("9 月 30 日", TimeText.ago(at(2026, 9, 30, 9, 0), now))
        assertEquals("2024 年 3 月 8 日", TimeText.ago(at(2024, 3, 8, 9, 0), now))
    }

    // ---------- 分类与 MIME ----------

    @Test
    fun kind_and_mime_by_extension() {
        assertEquals(FileKind.VIDEO, MimeMap.kindOf("海边日落.MP4"))
        assertEquals("video/mp4", MimeMap.mimeOf("海边日落.MP4"))
        assertEquals(FileKind.IMAGE, MimeMap.kindOf("a.jpg"))
        assertEquals(FileKind.AUDIO, MimeMap.kindOf("a.flac"))
        assertEquals(FileKind.APK, MimeMap.kindOf("猪猪传送门.apk"))
        assertEquals(FileKind.DOC, MimeMap.kindOf("说明书.pdf"))
        assertEquals(FileKind.ARCHIVE, MimeMap.kindOf("备份.zip"))
        assertEquals(FileKind.OTHER, MimeMap.kindOf("设计稿.psd"))
        assertEquals("application/octet-stream", MimeMap.mimeOf("设计稿.psd"))
    }

    @Test
    fun mime_without_extension_is_octet_stream() {
        assertEquals(FileKind.OTHER, MimeMap.kindOf("无扩展名"))
        assertEquals("", MimeMap.extensionOf("无扩展名"))
        assertEquals("application/octet-stream", MimeMap.mimeOf("无扩展名"))
    }

    // ---------- 文件名净化 ----------

    @Test
    fun name_drops_directories_and_traversal() {
        assertEquals("passwd", NameText.sanitize("../../etc/passwd"))
        assertEquals("passwd", NameText.sanitize("..\\..\\windows\\passwd"))
        assertEquals("a.jpg", NameText.sanitize("/sdcard/DCIM/a.jpg"))
    }

    @Test
    fun name_rejects_empty_and_dot_names() {
        assertNull(NameText.sanitize(null))
        assertNull(NameText.sanitize(""))
        assertNull(NameText.sanitize(".."))
        assertNull(NameText.sanitize("   "))
    }

    @Test
    fun name_strips_control_chars_and_hidden_dot() {
        assertEquals("hidden.jpg", NameText.sanitize(".hidden\u0000.jpg"))
        assertEquals("报告 v2.pdf", NameText.sanitize("  报告\u0007  v2.pdf  "))
    }

    @Test
    fun name_keeps_literal_case() {
        // 原型踩过这个坑：字体把 .mp4 画成 .MP4，看起来像篡改了真实文件名
        assertEquals("IMG_20261026.JPG", NameText.sanitize("IMG_20261026.JPG"))
    }

    @Test
    fun name_dedupes_with_counter_and_keeps_extension_last() {
        val existing = listOf("海边.mp4", "海边 (2).mp4")
        assertEquals("海边 (3).mp4", NameText.unique("海边.mp4", existing))
        assertEquals("新的.mp4", NameText.unique("新的.mp4", existing))
    }

    @Test
    fun name_clamp_keeps_extension() {
        val long = "文".repeat(160) + ".mp4"
        val out = NameText.sanitize(long)!!
        assertTrue(out.length <= 100)
        assertTrue(out.endsWith(".mp4"))
    }

    // ---------- 索引编解码 ----------

    @Test
    fun index_round_trip_survives_nasty_names() {
        val records = listOf(
            Record(1, "海边日落_2026.mp4", 428L * 1048576, 1_700_000_000_000, "content://media/external/downloads/12"),
            Record(2, "带 空格 和 % 号.txt", 12, 1_700_000_100_000, "file:///storage/emulated/0/Download/猪猪传送门/x.txt"),
            Record(3, "制表符\t换行\n的名字.apk", 99, 1_700_000_200_000, "content://media/external/downloads/13")
        )
        val decoded = IndexCodec.decode(IndexCodec.encode(records))
        assertEquals(records, decoded)
    }

    @Test
    fun index_ignores_broken_lines() {
        val text = """
            #zz1
            1	ok.mp4	10	20	content://a
            坏行没有字段
            2	bad.mp4	not_a_number	20	content://b
            3	ok2.mp4	30	40	content://c
        """.trimIndent()
        val decoded = IndexCodec.decode(text)
        assertEquals(listOf(1L, 3L), decoded.map { it.id })
    }

    @Test
    fun index_empty_input_is_empty_list() {
        assertTrue(IndexCodec.decode("").isEmpty())
        assertTrue(IndexCodec.decode("#zz1\n").isEmpty())
    }

    // ---------- 流搬运 ----------

    @Test
    fun pump_honours_limit() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        val copied = StreamPump.copy(data.inputStream(), out, limit = 40_000)
        assertEquals(40_000L, copied)
        assertEquals(40_000, out.size())
    }

    @Test
    fun pump_reports_monotonic_progress() {
        val data = ByteArray(300_000) { 1 }
        val seen = ArrayList<Long>()
        StreamPump.copy(data.inputStream(), java.io.ByteArrayOutputStream(), onProgress = { seen.add(it) })
        assertTrue(seen.size >= 2)
        assertEquals(seen.sorted(), seen)
        assertEquals(300_000L, seen.last())
    }

    @Test
    fun pump_stops_at_eof_when_no_limit() {
        val out = java.io.ByteArrayOutputStream()
        val copied = StreamPump.copy("hello".byteInputStream(), out)
        assertEquals(5L, copied)
        assertEquals("hello", out.toString("UTF-8"))
    }

    // ---------- 模板填充 ----------

    @Test
    fun template_replaces_known_and_keeps_unknown() {
        val out = PageTemplate.render("品牌={{BRAND}} 地址={{URL}} 未知={{MISS}}", mapOf("BRAND" to "猪猪传送门", "URL" to "http://10.0.0.2:8080"))
        assertEquals("品牌=猪猪传送门 地址=http://10.0.0.2:8080 未知={{MISS}}", out)
    }

    @Test
    fun template_does_not_rehydrate_replaced_values() {
        // 值里带 {{NAME}} 时不能被再替换一次
        val out = PageTemplate.render("{{A}}", mapOf("A" to "{{B}}", "B" to "X"))
        assertEquals("{{B}}", out)
    }

    @Test
    fun template_escapes_html() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;", PageTemplate.escape("<script>alert('x')</script>"))
    }

    // ---------- id 分配 ----------

    @Test
    fun id_uses_timestamp_when_time_moves_forward() {
        val ids = IdSource()
        assertEquals(1000L, ids.next(1000))
        assertEquals(1001L, ids.next(1001))
    }

    @Test
    fun id_stays_unique_within_the_same_millisecond() {
        // 真实事故：30 路并发上传出现 4 对相同 id，删除按 id 过滤，
        // 结果「删一个少了两个」。同一毫秒内必须自己往后加一。
        val ids = IdSource()
        val seen = (1..30).map { ids.next(1000) }
        assertEquals(30, seen.toSet().size)
        assertEquals(seen.sorted(), seen)
    }

    @Test
    fun id_does_not_go_backwards_when_clock_rewinds() {
        val ids = IdSource()
        ids.next(5000)
        // 系统对时、跨时区都可能让时间戳回退，回退时不能倒着发号
        assertEquals(5001L, ids.next(4000))
    }

    @Test
    fun id_seed_avoids_colliding_with_existing_records() {
        val ids = IdSource()
        ids.seed(9000)
        assertEquals(9001L, ids.next(1000))
        // 时间戳追上之后重新按时间走
        assertEquals(9500L, ids.next(9500))
    }
}
