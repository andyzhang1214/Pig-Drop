package com.zhuzhu.sudi.core

/**
 * 一条已接收记录。
 *
 * [uri] 是打开文件时要用的地址：Android 10+ 是 `content://media/...`（MediaStore），
 * 更低版本是 `file:///storage/emulated/0/Download/猪猪速递/xxx`（走 FileProvider 授权）。
 * 两种情况都存字符串，索引文件才能原样落盘。
 */
data class Record(
    val id: Long,
    val name: String,
    val size: Long,
    val time: Long,
    val uri: String,
    val source: String = "手机"
)
