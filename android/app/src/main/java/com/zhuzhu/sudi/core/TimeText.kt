package com.zhuzhu.sudi.core

import java.util.Calendar

/**
 * 相对时间文案。
 *
 * 三条约束来自原型：
 * 1. 不显示秒——电视上没人盯着秒数看，「刚刚」比「3 秒前」稳；
 * 2. 24 小时以内一律说小时，不看日历日：凌晨 00:10 看昨晚 23:50 传的文件，
 *    「20 分钟前」比「昨天」有用得多；反过来 29 小时前的文件说「昨天」才对；
 * 3. 超过一周给日期，因为「9 天前」对找回文件没有帮助。
 */
object TimeText {

    fun ago(then: Long, now: Long): String {
        val diff = now - then
        if (diff < 0) return "刚刚"
        if (diff < 60_000L) return "刚刚"

        val minutes = diff / 60_000L
        if (minutes < 60L) return "$minutes 分钟前"
        if (diff < 24 * 3_600_000L) return "${diff / 3_600_000L} 小时前"

        val days = calendarDaysBetween(then, now)
        if (days <= 1L) return "昨天"
        if (days < 7L) return "$days 天前"

        val cal = Calendar.getInstance().apply { timeInMillis = then }
        val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return if (year == thisYear) "$month 月 $day 日" else "$year 年 $month 月 $day 日"
    }

    /** 相差多少个本地日历日：今天为 0，昨天为 1。 */
    private fun calendarDaysBetween(then: Long, now: Long): Long {
        val a = Calendar.getInstance().apply {
            timeInMillis = then
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val b = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 用 DAY_OF_YEAR 差 + 年差，避开夏令时导致的 23/25 小时「一天」
        var days = (b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR)).toLong()
        var year = a.get(Calendar.YEAR)
        while (year < b.get(Calendar.YEAR)) {
            days += if (isLeap(year)) 366 else 365
            year++
        }
        return days
    }

    private fun isLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
