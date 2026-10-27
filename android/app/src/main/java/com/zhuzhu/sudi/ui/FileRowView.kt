package com.zhuzhu.sudi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.zhuzhu.sudi.R
import com.zhuzhu.sudi.core.FileKind
import com.zhuzhu.sudi.core.MimeMap
import com.zhuzhu.sudi.core.Record
import com.zhuzhu.sudi.core.SizeText
import com.zhuzhu.sudi.core.TimeText

/**
 * 一行文件。
 *
 * 三个焦点位：整行 / 打开 / 删除。遥控器左右键在行内推进、上下键换行，
 * 「删第 5 个」和「打开第 5 个」的按键次数因此是对称的。
 *
 * 焦点落在行内按钮时，行左侧会亮一条强调色竖条——否则用户只知道按钮被选中了，
 * 不知道在动哪一行。
 */
@SuppressLint("ViewConstructor")
class FileRowView(
    context: Context,
    private val palette: Palette
) : FrameLayout(context) {

    interface Listener {
        fun onRowActivate(row: FileRowView)
        fun onOpenClick(row: FileRowView)
        fun onDeleteClick(row: FileRowView)
    }

    var listener: Listener? = null
    var record: Record? = null
        private set

    private val content = LinearLayout(context)
    private val icon = ImageView(context)
    private val nameView = TextView(context)
    private val subView = TextView(context)
    private val sizeView = TextView(context)
    val openButton: TextView = makeButton(context, "打开")
    val deleteButton: TextView = makeButton(context, "删除")
    private val bar = View(context)

    var manageMode = false
        private set
    var selected = false
        private set

    init {
        clipChildren = false
        clipToPadding = false

        content.orientation = LinearLayout.HORIZONTAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPadding(Draw.dp(context, 12f), 0, Draw.dp(context, 12f), 0)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, Draw.dp(context, 58f)))

        val iconSize = Draw.dp(context, 34f)
        icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.setPadding(
            Draw.dp(context, 7f), Draw.dp(context, 7f),
            Draw.dp(context, 7f), Draw.dp(context, 7f)
        )
        content.addView(icon)

        val mid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = Draw.dp(context, 12f)
            }
        }
        nameView.apply {
            textSize = 15f
            typeface = palette.body
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        subView.apply {
            textSize = 10.5f
            typeface = palette.mono
            maxLines = 1
            includeFontPadding = false
        }
        mid.addView(nameView)
        mid.addView(subView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Draw.dp(context, 3f) })
        content.addView(mid)

        sizeView.apply {
            textSize = 11f
            typeface = palette.mono
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(Draw.dp(context, 54f), LayoutParams.WRAP_CONTENT)
                .apply { marginStart = Draw.dp(context, 10f) }
        }
        content.addView(sizeView)

        content.addView(openButton)
        content.addView(deleteButton)

        // 左侧强调条绝对定位，出现/隐藏时不会挤动整行内容
        addView(bar, LayoutParams(Draw.dp(context, 2f), Draw.dp(context, 34f)).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        })
        bar.visibility = View.INVISIBLE
        bar.setBackgroundColor(palette.accent)

        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // 整行自己是第一个焦点位，所以用 BEFORE_DESCENDANTS：
        // AFTER 的话 requestFocus() 会被转交给行内第一个按钮，"整行"这一档就永远聚焦不到。
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS

        setOnClickListener { listener?.onRowActivate(this) }
        openButton.setOnClickListener { listener?.onOpenClick(this) }
        deleteButton.setOnClickListener { listener?.onDeleteClick(this) }

        setOnFocusChangeListener { _, hasFocus ->
            setFocusedRing(hasFocus)
            refreshActiveState()
        }
        val childFocus = View.OnFocusChangeListener { _, _ ->
            refreshActiveState()
            refreshButtonColors()
        }
        openButton.onFocusChangeListener = childFocus
        deleteButton.onFocusChangeListener = childFocus
    }

    private fun makeButton(context: Context, label: String): TextView {
        val radius = Draw.dpF(context, palette.radiusDp * 0.7f)
        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Palettes.labelTypeface()
            includeFontPadding = false
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
            minWidth = Draw.dp(context, 50f)
            setPadding(Draw.dp(context, 11f), 0, Draw.dp(context, 11f), 0)
            background = Draw.states(
                normalFill = Color.TRANSPARENT,
                radiusPx = radius,
                lineColor = palette.line2,
                linePx = Draw.dp(context, 1f),
                focusFill = palette.surface2,
                focusLine = palette.accent,
                focusLinePx = Draw.dp(context, 2f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Draw.dp(context, 30f)
            ).apply { marginStart = Draw.dp(context, 8f) }
        }
    }

    fun bind(record: Record, now: Long, manage: Boolean, selected: Boolean) {
        this.record = record
        this.manageMode = manage
        this.selected = selected

        nameView.text = record.name
        subView.text = "${TimeText.ago(record.time, now)} · 来自${record.source}"
        sizeView.text = SizeText.of(record.size)

        openButton.visibility = if (manage) View.GONE else View.VISIBLE
        deleteButton.visibility = if (manage) View.GONE else View.VISIBLE
        openButton.isFocusable = !manage
        deleteButton.isFocusable = !manage

        nameView.setTextColor(palette.ink)
        subView.setTextColor(palette.ink3)
        sizeView.setTextColor(palette.ink2)

        applyIcon()
        setFocusedRing(isFocused)
        refreshActiveState()
        refreshButtonColors()
    }

    /** 焦点落在行内按钮时，把整行标记为「正在操作」。 */
    fun refreshActiveState() {
        val childFocused = openButton.hasFocus() || deleteButton.hasFocus()
        bar.visibility = if (childFocused && !manageMode) View.VISIBLE else View.INVISIBLE
        content.setBackgroundColor(if (childFocused) palette.hover else Color.TRANSPARENT)
    }

    fun refreshButtonColors() {
        openButton.setTextColor(if (openButton.hasFocus()) palette.ink else palette.ink2)
        deleteButton.setTextColor(if (deleteButton.hasFocus()) palette.danger else palette.ink3)
    }

    fun setFocusedRing(enabled: Boolean) {
        background = when {
            enabled -> Draw.rect(
                Color.TRANSPARENT,
                Draw.dpF(context, palette.radiusDp),
                Draw.dp(context, 2f),
                palette.accent
            )
            selected -> Draw.rect(
                Draw.mix(palette.accent, palette.bg, 0.10f),
                Draw.dpF(context, palette.radiusDp)
            )
            else -> Draw.rect(Color.TRANSPARENT, Draw.dpF(context, palette.radiusDp))
        }
    }

    /** 0 = 整行，1 = 打开，2 = 删除。 */
    fun columnView(column: Int): View = when (column) {
        1 -> openButton
        2 -> deleteButton
        else -> this
    }

    fun columnOf(view: View): Int = when (view) {
        openButton -> 1
        deleteButton -> 2
        else -> 0
    }

    private fun applyIcon() {
        val kind = record?.let { MimeMap.kindOf(it.name) } ?: FileKind.OTHER
        val radius = Draw.dpF(context, palette.radiusDp * 0.55f)
        if (manageMode) {
            icon.setImageResource(R.drawable.ic_check)
            if (selected) {
                icon.background = Draw.rect(palette.accent, radius)
                icon.setColorFilter(palette.accentInk)
            } else {
                icon.background = Draw.rect(
                    Color.TRANSPARENT, radius, Draw.dp(context, 1.5f), palette.ink3
                )
                icon.setColorFilter(Color.TRANSPARENT)
            }
        } else {
            icon.setImageResource(iconFor(kind))
            icon.background = Draw.rect(
                Color.TRANSPARENT, radius, Draw.dp(context, 1f), palette.line2
            )
            icon.setColorFilter(palette.accent)
        }
    }

    private fun iconFor(kind: FileKind): Int = when (kind) {
        FileKind.VIDEO -> R.drawable.ic_kind_video
        FileKind.IMAGE -> R.drawable.ic_kind_image
        FileKind.AUDIO -> R.drawable.ic_kind_audio
        FileKind.APK -> R.drawable.ic_kind_apk
        FileKind.DOC -> R.drawable.ic_kind_doc
        else -> R.drawable.ic_kind_other
    }
}
