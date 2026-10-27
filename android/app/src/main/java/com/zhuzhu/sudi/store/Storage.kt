package com.zhuzhu.sudi.store

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.zhuzhu.sudi.core.MimeMap
import com.zhuzhu.sudi.core.NameText
import com.zhuzhu.sudi.core.Record
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * 文件落地。
 *
 * 用户选的是「系统下载目录」，所以优先写 `Download/PigDrop/`——电视自带的文件管理器
 * 能看到、能往 U 盘拷。三档策略，从好到次：
 *
 * 1. **Android 10+**：走 MediaStore，不需要任何存储权限，文件对其它应用可见；
 * 2. **Android 6~9 且拿到存储权限**：直接写公共下载目录（这些版本的公共目录就是普通路径）；
 * 3. **权限被拒或目录不可写**：退回应用私有目录。功能不残废（照样能「打开」交给播放器），
 *    只是文件管理器里看不到，界面上会如实说明。
 */
class Storage(private val context: Context) {

    data class Target(
        val name: String,
        val uri: String,
        val stream: OutputStream,
        val commit: () -> Unit,
        val abort: () -> Unit
    )

    enum class Location(val label: String) {
        PUBLIC_DOWNLOADS("Download/$FOLDER"),
        APP_PRIVATE("应用私有目录（文件管理器看不到）")
    }

    /** 当前会落到哪儿，设置面板里如实显示。 */
    fun location(): Location = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Location.PUBLIC_DOWNLOADS
        publicDirWritable() -> Location.PUBLIC_DOWNLOADS
        else -> Location.APP_PRIVATE
    }

    fun requiresPermission(): Boolean =
        Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.Q

    fun begin(name: String): Target {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) beginMediaStore(name)
        else if (publicDirWritable()) beginPublicFile(name)
        else beginPrivateFile(name)
    }

    // ---------- Android 10+：MediaStore ----------

    private fun beginMediaStore(name: String): Target {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MimeMap.mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            // 先标记为「还在写」：别人扫这个目录时不会看到半截文件
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法在下载目录创建文件")
        // MediaStore 碰到同名文件会自己改成「名字 (1).jpg」，而且不告诉我们。
        // 如果这里还沿用请求的名字，列表里显示的名字和下载目录里的实际文件就对不上
        // （用户去文件管理器里找「海报图.jpg」，找到的是「海报图 (1).jpg」）。
        val actual = displayNameOf(uri) ?: name
        val stream = resolver.openOutputStream(uri)
            ?: run {
                resolver.delete(uri, null, null)
                throw IOException("无法写入下载目录")
            }

        return Target(
            name = actual,
            uri = uri.toString(),
            stream = stream,
            commit = {
                runCatching { stream.flush() }
                runCatching { stream.close() }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            },
            abort = {
                runCatching { stream.close() }
                runCatching { resolver.delete(uri, null, null) }
            }
        )
    }

    // ---------- Android 6~9：公共下载目录 ----------

    private fun beginPublicFile(name: String): Target {
        val dir = publicDir()
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录 ${dir.absolutePath}")
        return fileTarget(dir, name)
    }

    private fun beginPrivateFile(name: String): Target {
        val dir = File(context.filesDir, PRIVATE_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建私有目录")
        return fileTarget(dir, name)
    }

    /**
     * 建一个**不会覆盖已有文件**的目标。
     *
     * `FileOutputStream(file)` 遇到同名文件是直接截断的——应用重装后索引没了、文件还在，
     * 这时再传一个同名文件就会把旧的悄悄覆盖掉，用户完全看不出来。所以先按目录里已有
     * 名字避让（规则和列表里的 `(2)(3)` 一致）。
     */
    private fun fileTarget(dir: File, name: String): Target {
        val file = uniqueFile(dir, name)
        val stream = FileOutputStream(file)
        return Target(
            name = file.name,
            uri = "file://${file.absolutePath}",
            stream = stream,
            commit = { runCatching { stream.flush() }; runCatching { stream.close() } },
            abort = {
                runCatching { stream.close() }
                runCatching { file.delete() }
            }
        )
    }

    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        return File(dir, NameText.unique(name, dir.list()?.toList() ?: emptyList()))
    }

    /** 建完之后问 MediaStore 实际用了什么名字。 */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    // ---------- 删除 / 打开 ----------

    fun delete(record: Record): Boolean {
        return try {
            if (record.uri.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(record.uri), null, null) > 0
            } else {
                fileOf(record)?.delete() ?: false
            }
        } catch (e: Exception) {
            // Android 11+ 对「别的应用创建的文件」会抛 RecoverableSecurityException，
            // 需要系统弹窗二次确认。这里如实返回失败，让界面提示。
            false
        }
    }

    /** 交给系统播放器/安装器时用的可授权 Uri。 */
    fun accessUri(record: Record): Uri? {
        return try {
            if (record.uri.startsWith("content://")) {
                Uri.parse(record.uri)
            } else {
                val file = fileOf(record) ?: return null
                if (!file.exists()) return null
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun exists(record: Record): Boolean {
        if (record.uri.startsWith("content://")) return true
        return fileOf(record)?.exists() == true
    }

    private fun fileOf(record: Record): File? {
        val path = record.uri.removePrefix("file://")
        return if (path.isEmpty()) null else File(path)
    }

    private fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    private fun publicDirWritable(): Boolean {
        if (requiresPermission() && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val dir = publicDir()
            if (!dir.exists()) dir.mkdirs()
            dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val FOLDER = "PigDrop"
        private const val PRIVATE_DIR = "received"
    }
}
