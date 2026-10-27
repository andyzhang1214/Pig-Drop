package com.zhuzhu.sudi.ui

import android.graphics.Typeface

/**
 * 一套主题。
 *
 * 颜色值是把原型里的 oklch 精确换算成 sRGB 得到的（不是随手挑的近色），
 * 所以两套主题在电视上的观感和原型一致：夜航是低明度冷底 + 荧光青绿强调，
 * 素纸是暖米底 + 陶土红强调。
 *
 * 唯一的例外是 `ink3`：原型里的值按桌面距离看是合适的，但电视是 3 米外看
 * 1080p，实测夜航 `ink3` 落在聚焦行的底色上只有 3.73:1、素纸落在暖米底上
 * 只有 3.16:1，时间行和底部操作提示基本糊成一片。两个值都往「离底色更远」
 * 的方向调了一档，现在各自最低 4.66:1 / 4.64:1。改的是明度，色相没动。
 *
 * 字体不打包：电视上中文占绝大多数，自带中文字体会让包体膨胀好几 MB，
 * 而系统本身就带思源黑体。这里只用系统的「窄体」「衬线」「等宽」做气质区分。
 */
data class Palette(
    val key: String,
    val label: String,
    val isDark: Boolean,
    val bg: Int,
    val bg2: Int,
    val surface: Int,
    val surface2: Int,
    val ink: Int,
    val ink2: Int,
    val ink3: Int,
    val accent: Int,
    val accent2: Int,
    val accentInk: Int,
    val danger: Int,
    val line: Int,
    val line2: Int,
    val hover: Int,
    val qrBg: Int,
    val qrFg: Int,
    val radiusDp: Float,
    val display: Typeface,
    val body: Typeface,
    val mono: Typeface
)

object Palettes {

    private val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val SERIF: Typeface = Typeface.create("serif", Typeface.BOLD)
    private val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val MONO: Typeface = Typeface.MONOSPACE

    val NOIR = Palette(
        key = "noir",
        label = "夜航",
        isDark = true,
        bg = 0xFF070D14.toInt(),
        bg2 = 0xFF0E161F.toInt(),
        surface = 0xFF131D27.toInt(),
        surface2 = 0xFF1D2935.toInt(),
        ink = 0xFFF0F4F9.toInt(),
        ink2 = 0xFFB7BFC7.toInt(),
        ink3 = 0xFF86929E.toInt(),   // oklch(65.42% 0.0227 248.17)，聚焦行底上 4.66:1
        accent = 0xFF20F4BB.toInt(),
        accent2 = 0xFF009871.toInt(),
        accentInk = 0xFF03160F.toInt(),
        danger = 0xFFF75C61.toInt(),
        line = 0xFF20252B.toInt(),
        line2 = 0xFF383D43.toInt(),
        hover = 0xFF13191F.toInt(),
        qrBg = 0xFFF3F5F8.toInt(),
        qrFg = 0xFF070E16.toInt(),
        radiusDp = 14f,
        display = CONDENSED,
        body = SANS,
        mono = MONO
    )

    val PAPER = Palette(
        key = "paper",
        label = "素纸",
        isDark = false,
        bg = 0xFFF5EFE6.toInt(),
        bg2 = 0xFFEDE5D9.toInt(),
        surface = 0xFFFDFAF4.toInt(),
        surface2 = 0xFFF3ECE1.toInt(),
        ink = 0xFF2F241C.toInt(),
        ink2 = 0xFF605248.toInt(),
        ink3 = 0xFF6D645B.toInt(),   // oklch(50.89% 0.0181 67.40)，暖米底上 4.64:1
        accent = 0xFFAF5028.toInt(),
        accent2 = 0xFFD78862.toInt(),
        accentInk = 0xFFFFFBF4.toInt(),
        danger = 0xFFB72121.toInt(),
        line = 0xFFDBD4CA.toInt(),
        line2 = 0xFFBCB4AB.toInt(),
        hover = 0xFFEDE6DD.toInt(),
        qrBg = 0xFFFFFDFA.toInt(),
        qrFg = 0xFF2A1F17.toInt(),
        radiusDp = 10f,
        display = SERIF,
        body = SANS,
        mono = MONO
    )

    val ALL = listOf(NOIR, PAPER)

    fun byKey(key: String?): Palette = ALL.firstOrNull { it.key == key } ?: NOIR

    /** 列表里按钮/标签用的中黑，比正文字重一档。 */
    fun labelTypeface(): Typeface = SANS_MEDIUM
}
