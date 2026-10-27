package com.zhuzhu.sudi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.zhuzhu.sudi.core.Record

/**
 * 文件列表 + 遥控器导航。
 *
 * 导航**不用**系统的几何最近邻，而是显式规定：
 * - 行内 `←→`：整行 → 打开 → 删除，到头停住；
 * - `↑↓`：在同一列里换行，列位置保持不变；
 * - 第一行往上出列表（交给外层去找「管理」按钮），末行往下**封底**——
 *   原型里就是这里出过 bug：几何回退会把焦点甩到左边栏的按钮上。
 *
 * 封底的做法是返回当前焦点视图本身：框架认为「我找到的就是你自己」，于是不动。
 */
@SuppressLint("ViewConstructor")
class FileListView(
    context: Context,
    private val palette: Palette
) : LinearLayout(context) {

    interface Listener {
        fun onActivate(record: Record)
        fun onOpen(record: Record)
        fun onDelete(record: Record)
        fun onSelectionChanged()
    }

    var listener: Listener? = null

    private val rows = ArrayList<FileRowView>()
    private var records: List<Record> = emptyList()

    var manageMode: Boolean = false
        private set

    /** 管理模式下按右键要去的地方（底部操作条的「全选」）。遥控器没有长按和快捷键，
     *  如果只能一路按到下键走到列表末尾再下一格，删列表中间的一项要按十几次。 */
    var manageExitTarget: View? = null

    /** 从整行按左键出去落到哪（左栏的「设置」）。不靠几何猜：几何会挑到「放大二维码」，
     *  因为两个按钮到行的距离几乎一样，用户每次都猜不到会跳到哪个。 */
    var leftExitTarget: View? = null

    val selectedIds = LinkedHashSet<Long>()

    init {
        orientation = VERTICAL
        isFocusable = false
    }

    fun render(list: List<Record>, manage: Boolean, keepSelection: Boolean = true) {
        // 再拷一份：仓库已经给的是快照，但这里多一层就能彻底不依赖调用方守规矩——
        // 早先正是边遍历边上传来新记录，抛了 ConcurrentModificationException。
        val snapshot = list.toList()
        records = snapshot
        manageMode = manage
        if (!keepSelection) selectedIds.clear()

        removeAllViews()
        rows.clear()

        for (record in snapshot) {
            val row = FileRowView(context, palette)
            row.listener = rowListener
            row.bind(record, System.currentTimeMillis(), manage, manage && record.id in selectedIds)
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rows.add(row)
        }
    }

    /** 重新绑定（时间文案会过期），不重建视图。 */
    fun refreshLabels() {
        val now = System.currentTimeMillis()
        for (row in rows) {
            val record = row.record ?: continue
            row.bind(record, now, manageMode, manageMode && record.id in selectedIds)
        }
    }

    fun selectedRecords(): List<Record> = records.filter { it.id in selectedIds }

    fun rowAt(index: Int): FileRowView? = rows.getOrNull(index)

    fun indexOfRow(row: FileRowView): Int = rows.indexOf(row)

    fun rowOf(view: View): FileRowView? {
        var current: View? = view
        while (current != null && current.parent !== this) {
            current = current.parent as? View
        }
        return current as? FileRowView
    }

    fun focusFirst(): Boolean {
        val first = rows.firstOrNull() ?: return false
        return first.requestFocus()
    }

    /** 删除后把焦点放到原位置，列表空了就交给外层。 */
    fun focusNear(index: Int): Boolean {
        val target = rows.getOrNull(index) ?: rows.lastOrNull() ?: return false
        return target.requestFocus()
    }

    private val rowListener = object : FileRowView.Listener {
        override fun onRowActivate(row: FileRowView) {
            val record = row.record ?: return
            if (manageMode) {
                if (record.id in selectedIds) selectedIds.remove(record.id) else selectedIds.add(record.id)
                row.bind(record, System.currentTimeMillis(), true, record.id in selectedIds)
                listener?.onSelectionChanged()
            } else {
                listener?.onActivate(record)
            }
        }

        override fun onOpenClick(row: FileRowView) {
            row.record?.let { listener?.onOpen(it) }
        }

        override fun onDeleteClick(row: FileRowView) {
            row.record?.let { listener?.onDelete(it) }
        }
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val row = rowOf(focused) ?: return super.focusSearch(focused, direction)
        val column = row.columnOf(focused)
        val index = rows.indexOf(row)
        if (index < 0) return super.focusSearch(focused, direction)

        return when (direction) {
            FOCUS_RIGHT -> when {
                // 管理模式下没有行内按钮，右键直达底部操作条
                manageMode -> manageExitTarget ?: super.focusSearch(focused, direction)
                column < 2 -> row.columnView(column + 1)
                else -> focused
            }
            FOCUS_LEFT -> when {
                !manageMode && column > 0 -> row.columnView(column - 1)
                leftExitTarget != null -> leftExitTarget
                else -> super.focusSearch(focused, direction)
            }
            FOCUS_DOWN -> rows.getOrNull(index + 1)?.columnView(if (manageMode) 0 else column) ?: focused
            FOCUS_UP -> if (index > 0) {
                rows[index - 1].columnView(if (manageMode) 0 else column)
            } else {
                super.focusSearch(focused, direction)
            }
            else -> super.focusSearch(focused, direction)
        }
    }
}
