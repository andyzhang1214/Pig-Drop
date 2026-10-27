package com.zhuzhu.sudi.core

/** 列表里用的粗分类，决定图标和「交给谁打开」。 */
enum class FileKind { VIDEO, IMAGE, AUDIO, APK, DOC, ARCHIVE, OTHER }

object MimeMap {

    private val MIME = mapOf(
        // 视频
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "webm" to "video/webm",
        "flv" to "video/x-flv", "ts" to "video/mp2t", "3gp" to "video/3gpp",
        "wmv" to "video/x-ms-wmv", "rmvb" to "video/vnd.rn-realvideo",
        // 图片
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        // 音频
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac",
        "flac" to "audio/flac", "wav" to "audio/wav", "ogg" to "audio/ogg",
        "amr" to "audio/amr", "wma" to "audio/x-ms-wma",
        // 文档
        "pdf" to "application/pdf", "txt" to "text/plain", "md" to "text/plain",
        "csv" to "text/csv", "json" to "application/json", "xml" to "text/xml",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "epub" to "application/epub+zip",
        // 安装包
        "apk" to "application/vnd.android.package-archive",
        // 压缩包
        "zip" to "application/zip", "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed", "tar" to "application/x-tar",
        "gz" to "application/gzip"
    )

    private val DOCS = setOf(
        "pdf", "txt", "md", "csv", "json", "xml", "doc", "docx",
        "xls", "xlsx", "ppt", "pptx", "epub"
    )

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun kindOf(name: String): FileKind {
        val ext = extensionOf(name)
        return when {
            ext == "apk" -> FileKind.APK
            ext in DOCS -> FileKind.DOC
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> FileKind.ARCHIVE
            mimeOrNull(ext)?.startsWith("video/") == true -> FileKind.VIDEO
            mimeOrNull(ext)?.startsWith("image/") == true -> FileKind.IMAGE
            mimeOrNull(ext)?.startsWith("audio/") == true -> FileKind.AUDIO
            else -> FileKind.OTHER
        }
    }

    fun mimeOf(name: String): String {
        val ext = extensionOf(name)
        return mimeOrNull(ext) ?: "application/octet-stream"
    }

    private fun mimeOrNull(ext: String): String? = MIME[ext]
}
