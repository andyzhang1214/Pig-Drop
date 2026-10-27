package com.zhuzhu.sudi.core

import java.util.Locale

/**
 * 体积文案。
 *
 * 规则照抄原型：不到 1 KB 直接给字节数；超过按 1024 进位；一位小数只在 10 以下保留，
 * 因为「428 MB」比「428.3 MB」在电视上更容易一眼扫到量级。
 */
object SizeText {

    private val UNITS = arrayOf("KB", "MB", "GB", "TB", "PB")

    fun of(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"

        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < UNITS.size - 1) {
            value /= 1024.0
            unit++
        }
        val number = if (value < 10.0) {
            // 9.95 会被 format 成 10.0，宁可退一位小数也不要出现「10.0 MB」这种别扭写法
            val rounded = Math.round(value * 10.0) / 10.0
            if (rounded >= 10.0) Math.round(value).toString()
            else String.format(Locale.US, "%.1f", rounded)
        } else {
            Math.round(value).toString()
        }
        return "$number ${UNITS[unit]}"
    }

    /** 列表头部的汇总体积，例如「67 MB」。 */
    fun totalOf(bytes: Long): String = of(bytes)
}
