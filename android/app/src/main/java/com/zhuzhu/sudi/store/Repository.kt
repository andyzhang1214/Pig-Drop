package com.zhuzhu.sudi.store

import android.content.Context
import com.zhuzhu.sudi.core.IdSource
import com.zhuzhu.sudi.core.IndexCodec
import com.zhuzhu.sudi.core.NameText
import com.zhuzhu.sudi.core.Record
import java.io.File

/**
 * 已接收文件的仓库：内存列表 + 落盘索引 + 变更通知。
 *
 * 索引存在应用私有目录（几十字节），文件本身在下载目录，两者分开——
 * 索引丢了可以重建，文件丢了才是真丢。
 *
 * ### 为什么到处加锁
 *
 * 写列表的是上传线程（NanoHTTPD 每条请求一个线程），读列表的是主线程
 * （`renderList` 重建行视图）。早先 `records` 直接把内部 ArrayList 递出去，
 * 主线程正遍历着、上传线程 `items.add(0, ...)` 一插，就抛
 * `ConcurrentModificationException`，连传多个文件必崩。所以：
 *
 * - `records` **只返回快照**（`toList()`），外部拿到的列表怎么遍历都安全；
 * - 所有增删都走同一把锁，索引文件的写入也放在锁里（两个并发上传同时
 *   写 `received-index.txt` 会把内容搅成半截）。
 */
class Repository(private val context: Context, private val storage: Storage) {

    private val indexFile = File(context.filesDir, "received-index.txt")
    private val items = ArrayList<Record>()

    private val lock = Any()

    /** id 分配器。和列表共用一把锁，所以并发上传不会拿到同一个 id。 */
    private val ids = IdSource()

    /** 已经认领但还没落地的文件名。并发上传同名文件时靠它避让。 */
    private val reserved = HashSet<String>()

    /** 列表发生变化（新增/删除/清空）时回调。**可能在非主线程**，调用方自己切。 */
    var onChange: (() -> Unit)? = null

    /** 快照。调用方拿到之后这个列表不会再变。 */
    val records: List<Record> get() = synchronized(lock) { items.toList() }

    fun load() {
        val loaded = runCatching {
            if (indexFile.exists()) IndexCodec.decode(indexFile.readText()) else emptyList()
        }.getOrDefault(emptyList())

        synchronized(lock) {
            items.clear()
            // 索引里可能有已经被外部删掉的文件（用户在文件管理器里删的），加载时清一遍
            for (record in loaded) {
                if (storage.exists(record)) items.add(record)
            }
            items.sortByDescending { it.time }
            ids.seed(items.maxOfOrNull { it.id } ?: 0L)
            persist()
        }
    }

    fun add(record: Record) {
        synchronized(lock) {
            items.add(0, record)
            reserved.remove(record.name)
            persist()
        }
        onChange?.invoke()
    }

    fun remove(ids: Collection<Long>): Int {
        val removed = synchronized(lock) {
            val targets = items.filter { it.id in ids }
            if (targets.isEmpty()) return 0
            var count = 0
            for (record in targets) {
                if (storage.delete(record)) count++
                items.remove(record)
            }
            persist()
            count
        }
        onChange?.invoke()
        return removed
    }

    fun clear(): Int = remove(records.map { it.id })

    fun nameTaken(name: String): Boolean = synchronized(lock) {
        items.any { it.name.equals(name, ignoreCase = true) }
    }

    fun uniqueName(name: String): String = synchronized(lock) {
        NameText.unique(name, items.map { it.name })
    }

    /**
     * 认领一个不冲突的文件名，并占位到落地为止。
     *
     * `uniqueName` 只看已落地的记录，两台手机同时传 `IMG_0001.jpg` 会拿到同一个名字，
     * 后者覆盖前者。认领把「还在传」的名字也算进占用，就不需要用户手动改名。
     */
    fun claimName(candidate: String): String = synchronized(lock) {
        val taken = items.map { it.name } + reserved
        val name = NameText.unique(candidate, taken)
        reserved.add(name)
        name
    }

    /** 传失败时把占位还回去。 */
    fun releaseName(name: String) {
        synchronized(lock) { reserved.remove(name) }
    }

    /** 单调递增且不重复的 id。 */
    fun nextId(): Long = synchronized(lock) { ids.next(System.currentTimeMillis()) }

    fun totalBytes(): Long = synchronized(lock) { items.sumOf { it.size } }

    private fun persist() {
        runCatching { indexFile.writeText(IndexCodec.encode(items)) }
    }
}
