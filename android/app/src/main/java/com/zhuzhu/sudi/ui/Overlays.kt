package com.zhuzhu.sudi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zhuzhu.sudi.R
import com.zhuzhu.sudi.core.MimeMap
import com.zhuzhu.sudi.core.Record
import com.zhuzhu.sudi.core.SizeText
import com.zhuzhu.sudi.core.TimeText

/**
 * 全部浮层：确认框、打开失败的交代、设置、放大的二维码。
 *
 * 都挂在同一个宿主里，**同一时刻只允许一个**——遥控器没有「多窗口」概念，
 * 两层浮层叠着会让返回键的语义变得不可预测。
 */
class Overlays(
    private val context: Context,
    private val host: FrameLayout,
    /** 浮层下面的主界面。弹浮层时要把它整片的焦点封死，否则遥控器的方向键会从浮层里
     *  「翻墙」回到背后的列表上——用户看着浮层，按 OK 却打开了后面的文件。 */
    private val stage: ViewGroup
) {

    var palette: Palette = Palettes.NOIR

    private var current: View? = null
    private var restoreFocus: View? = null

    fun isShowing(): Boolean = current != null

    fun dismiss(): Boolean {
        val view = current ?: return false
        host.removeView(view)
        current = null
        stage.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        restoreFocus?.requestFocus()
        restoreFocus = null
        return true
    }

    /** 记住从哪儿弹出来的，关掉后焦点回原位——否则用户会「丢」在界面上。 */
    fun rememberFocus(view: View?) {
        restoreFocus = view
    }

    // ---------- 确认框 ----------

    fun showConfirm(
        title: String,
        body: String,
        confirmLabel: String,
        onConfirm: () -> Unit
    ) {
        val p = palette
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), dp(24f), dp(26f), dp(20f))
            background = Draw.rect(p.surface, dpF(p.radiusDp * 1.1f), dp(1f), p.line2)
        }

        card.addView(UiKit.text(context, 20f, p.ink, p.display).apply { text = title })

        card.addView(UiKit.text(context, 13f, p.ink2, p.body).apply {
            text = body
            setLineSpacing(dpF(4f), 1f)
            setPadding(0, dp(10f), 0, 0)
        })

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(20f), 0, 0)
        }
        val cancel = chip("取消", p, danger = false)
        val confirm = chip(confirmLabel, p, danger = true)
        buttons.addView(cancel)
        buttons.addView(confirm)
        card.addView(buttons)

        // 默认焦点在「取消」：遥控器误触概率远高于鼠标，危险操作的默认位置必须是安全的那个
        cancel.setOnClickListener { dismiss() }
        confirm.setOnClickListener { dismiss(); onConfirm() }

        val holder = frame(card, widthDp = 400f)
        show(holder, cancel)
    }

    // ---------- 打开失败 ----------

    fun showOpenFailed(record: Record, message: String) {
        val p = palette
        // 必须自己有底：只有一层压暗的话，文字会和背后的列表叠在一起，谁也读不了
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40f), dp(30f), dp(40f), dp(26f))
            background = Draw.rect(p.surface, dpF(p.radiusDp * 1.1f), dp(1f), p.line2)
        }

        val kind = MimeMap.kindOf(record.name)
        card.addView(ImageView(context).apply {
            setImageResource(iconFor(kind))
            setColorFilter(p.accent)
            background = Draw.rect(Color.TRANSPARENT, dpF(p.radiusDp), dp(1f), p.line2)
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            layoutParams = LinearLayout.LayoutParams(dp(72f), dp(72f))
        })

        card.addView(UiKit.text(context, 22f, p.ink, p.body).apply {
            text = record.name
            maxLines = 2
            gravity = Gravity.CENTER
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(18f), 0, 0)
        })

        card.addView(UiKit.text(context, 11.5f, p.ink3, p.mono).apply {
            // 「3 分钟前 收到」中文读起来是断的，改成「收到于 3 分钟前」，三段之间都是 · 分隔
            text = "${SizeText.of(record.size)} · 收到于 ${TimeText.ago(record.time, System.currentTimeMillis())} · ${MimeMap.extensionOf(record.name).uppercase()}"
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        })

        // 不写一句「打开失败」就完事——给的是下一步能做什么
        card.addView(UiKit.text(context, 13f, p.danger, p.body).apply {
            text = message
            gravity = Gravity.CENTER
            setLineSpacing(dpF(5f), 1f)
            setPadding(0, dp(18f), 0, 0)
        })

        val back = chip("返回列表", p, danger = false)
        back.setOnClickListener { dismiss() }
        card.addView(back, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(34f)
        ).apply { topMargin = dp(22f) })

        show(frame(card, widthDp = 520f), back)
    }

    // ---------- 放大的二维码 ----------

    fun showBigQr(bitmap: android.graphics.Bitmap, url: String) {
        val p = palette
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28f), dp(28f), dp(28f), dp(24f))
            background = Draw.rect(p.qrBg, dpF(p.radiusDp * 1.2f))
        }
        column.addView(ImageView(context).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(dp(380f), dp(380f))
        })
        column.addView(UiKit.text(context, 15f, p.qrFg, p.mono).apply {
            text = url
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, 0)
        })
        column.addView(UiKit.text(context, 12f, p.qrFg, p.body).apply {
            text = "用手机相机扫这个码"
            alpha = 0.62f
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, 0)
        })

        val holder = FrameLayout(context).apply {
            setBackgroundColor(scrim())
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            setOnClickListener { dismiss() }
            // 浮层自己接焦点：否则焦点会留在背后那个「放大二维码」按钮上，露出一个没人用的焦点框
            isFocusable = true
            isFocusableInTouchMode = true
        }
        show(holder, holder)
    }

    // ---------- 设置 ----------

    fun showSettings(
        port: Int,
        url: String,
        locationLabel: String,
        onPickTheme: (String) -> Unit,
        onClearAll: () -> Unit
    ) {
        val p = palette
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), dp(24f), dp(26f), dp(20f))
            background = Draw.rect(p.surface, dpF(p.radiusDp * 1.1f), dp(1f), p.line2)
        }
        card.addView(UiKit.text(context, 20f, p.ink, p.display).apply { text = "设置" })

        // 主题
        card.addView(sectionLabel("主题"))
        val themeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }
        var firstChip: View? = null
        for (item in Palettes.ALL) {
            val chip = themeChip(item, item.key == p.key) { onPickTheme(item.key) }
            if (firstChip == null) firstChip = chip
            themeRow.addView(chip)
        }
        card.addView(themeRow)

        card.addView(infoRow("访问地址", url))
        card.addView(infoRow("保存位置", locationLabel))
        card.addView(infoRow("端口", port.toString()))

        val clear = chip("清空全部记录", p, danger = true)
        clear.setOnClickListener {
            dismiss()
            onClearAll()
        }
        card.addView(clear, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32f)
        ).apply { topMargin = dp(18f) })

        val close = chip("关闭", p, danger = false)
        close.setOnClickListener { dismiss() }
        card.addView(close, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32f)
        ).apply { topMargin = dp(10f) })

        val scroller = ScrollView(context).apply {
            isFillViewport = true
            addView(card, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val holder = FrameLayout(context).apply {
            setBackgroundColor(scrim())
            addView(scroller, FrameLayout.LayoutParams(dp(440f), dp(400f)).apply {
                gravity = Gravity.CENTER
            })
        }
        show(holder, firstChip ?: close)
    }

    // ---------- 内部构件 ----------

    private fun show(view: View, focusTarget: View?) {
        dismiss()
        // 把背后的界面整片封住，方向键只能在浮层内部走
        stage.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        host.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        current = view
        focusTarget?.requestFocus()
    }

    /**
     * 所有浮层背后那层压暗。浅色底上压得更轻——压太狠会把整屏压成灰砖，
     * 反而看不出底下还有东西。三个浮层必须用同一个值，否则前后翻会「忽明忽暗」。
     */
    private fun scrim(): Int =
        if (palette.isDark) 0xB3000000.toInt() else 0x99000000.toInt()

    private fun frame(content: View, widthDp: Float): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(scrim())
        isClickable = true
        addView(content, FrameLayout.LayoutParams(dp(widthDp), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun sectionLabel(text: String): TextView = UiKit.text(context, 11f, palette.ink3).apply {
        this.text = text
        letterSpacing = 0.12f
        setPadding(0, dp(18f), 0, dp(2f))
    }

    private fun infoRow(label: String, value: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12f), 0, 0)
        addView(UiKit.text(context, 12f, palette.ink3).apply { text = label })
        addView(UiKit.text(context, 12f, palette.ink2, palette.mono).apply {
            text = value
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun themeChip(item: Palette, active: Boolean, onClick: () -> Unit): TextView {
        val p = palette
        return TextView(context).apply {
            text = item.label
            textSize = 13f
            typeface = Palettes.labelTypeface()
            gravity = Gravity.CENTER
            includeFontPadding = false
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
            minWidth = dp(74f)
            setPadding(dp(14f), 0, dp(14f), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)
            ).apply { marginEnd = dp(10f) }
            background = if (active) {
                Draw.states(
                    normalFill = p.accent, radiusPx = dpF(p.radiusDp * 0.7f),
                    lineColor = p.accent, linePx = dp(1f),
                    focusFill = p.accent, focusLine = p.ink, focusLinePx = dp(2f)
                )
            } else {
                Draw.states(
                    normalFill = Color.TRANSPARENT, radiusPx = dpF(p.radiusDp * 0.7f),
                    lineColor = p.line2, linePx = dp(1f),
                    focusFill = p.surface2, focusLine = p.accent, focusLinePx = dp(2f)
                )
            }
            setTextColor(if (active) p.accentInk else p.ink2)
            setOnClickListener { onClick() }
        }
    }

    private fun chip(label: String, p: Palette, danger: Boolean): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Palettes.labelTypeface()
            gravity = Gravity.CENTER
            includeFontPadding = false
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
            minWidth = dp(78f)
            setPadding(dp(16f), 0, dp(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32f)
            ).apply { marginStart = dp(10f) }
            background = Draw.states(
                normalFill = Color.TRANSPARENT,
                radiusPx = dpF(p.radiusDp * 0.7f),
                lineColor = if (danger) Draw.withAlpha(p.danger, 0.5f) else p.line2,
                linePx = dp(1f),
                focusFill = if (danger) Draw.withAlpha(p.danger, 0.16f) else p.surface2,
                focusLine = if (danger) p.danger else p.accent,
                focusLinePx = dp(2f)
            )
            setTextColor(if (danger) p.danger else p.ink2)
        }
    }

    private fun dp(value: Float): Int = Draw.dp(context, value)

    private fun dpF(value: Float): Float = Draw.dpF(context, value)

    private fun iconFor(kind: com.zhuzhu.sudi.core.FileKind): Int = when (kind) {
        com.zhuzhu.sudi.core.FileKind.VIDEO -> R.drawable.ic_kind_video
        com.zhuzhu.sudi.core.FileKind.IMAGE -> R.drawable.ic_kind_image
        com.zhuzhu.sudi.core.FileKind.AUDIO -> R.drawable.ic_kind_audio
        com.zhuzhu.sudi.core.FileKind.APK -> R.drawable.ic_kind_apk
        com.zhuzhu.sudi.core.FileKind.DOC -> R.drawable.ic_kind_doc
        else -> R.drawable.ic_kind_other
    }
}
