package com.zhuzhu.sudi.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** 造控件的小工具，避免每个界面各写一遍 dp/sp 和背景。 */
object UiKit {

    fun text(
        context: Context,
        sizeSp: Float,
        color: Int,
        typeface: Typeface? = null,
        weight: Int = -1
    ): TextView = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        if (typeface != null) this.typeface = typeface
        if (weight >= 0) {
            paint.isFakeBoldText = weight > 0
        }
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

    /** 行内按钮：无内建样式，全靠背景状态与文字色。 */
    fun actionButton(context: Context, label: String, p: Palette): TextView {
        val radius = Draw.dpF(context, p.radiusDp * 0.8f)
        val linePx = Draw.dp(context, 1f)
        val focusPx = Draw.dp(context, 2f)

        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Palettes.labelTypeface()
            setPadding(
                Draw.dp(context, 12f), 0,
                Draw.dp(context, 12f), 0
            )
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
            includeFontPadding = false
            background = Draw.states(
                normalFill = android.graphics.Color.TRANSPARENT,
                radiusPx = radius,
                lineColor = p.line2,
                linePx = linePx,
                focusFill = p.surface2,
                focusLine = p.accent,
                focusLinePx = focusPx
            )
            setTextColor(p.ink2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Draw.dp(context, 30f)
            ).apply { marginStart = Draw.dp(context, 8f) }
        }
    }

    fun spacer(context: Context, weight: Float = 1f): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1).apply { this.weight = weight }
    }

    fun divider(context: Context, color: Int): View = View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Draw.dp(context, 1f))
    }
}
