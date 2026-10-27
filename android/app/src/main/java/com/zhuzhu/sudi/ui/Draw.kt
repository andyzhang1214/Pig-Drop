package com.zhuzhu.sudi.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue

/** 电视端所有「面」都是圆角矩形+描边，这里统一造，避免每处各写一遍。 */
object Draw {

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    fun dpF(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    )

    fun rect(fill: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fill)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /**
     * 焦点/悬停/常态三态背景。
     *
     * 电视上没有悬停，只有「焦点在你身上」这一种反馈，所以焦点态必须一眼可辨：
     * 描边换成强调色、底色提亮。删除按钮焦点态用危险色，让「按下去会发生什么」在按下前就看得见。
     */
    fun states(
        normalFill: Int,
        radiusPx: Float,
        lineColor: Int,
        linePx: Int,
        focusFill: Int,
        focusLine: Int,
        focusLinePx: Int
    ): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rect(focusFill, radiusPx, focusLinePx, focusLine))
        addState(intArrayOf(android.R.attr.state_pressed), rect(focusFill, radiusPx, focusLinePx, focusLine))
        addState(intArrayOf(), rect(normalFill, radiusPx, linePx, lineColor))
    }

    /** 纯色/斜线底，用于二维码衬板。 */
    fun solid(color: Int, radiusPx: Float): GradientDrawable = rect(color, radiusPx)

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun mix(top: Int, bottom: Int, topAlpha: Float): Int = Color.rgb(
        (Color.red(top) * topAlpha + Color.red(bottom) * (1 - topAlpha)).toInt(),
        (Color.green(top) * topAlpha + Color.green(bottom) * (1 - topAlpha)).toInt(),
        (Color.blue(top) * topAlpha + Color.blue(bottom) * (1 - topAlpha)).toInt()
    )
}
