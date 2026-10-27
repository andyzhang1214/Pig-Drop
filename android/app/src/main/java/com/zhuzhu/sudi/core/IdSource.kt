package com.zhuzhu.sudi.core

/**
 * 单调递增的 id 分配器。
 *
 * id 拿毫秒时间戳打底（好读，也天然接近「越新越大」），但**必须保证并发时不重复**。
 * 早先每条上传各自算一遍 `max(当前毫秒, 已有最大 id + 1)`，30 路并发实测出现 4 对
 * 同一个 id；而删除是按 id 过滤的，撞上就会一次删掉两个文件——现场表现是「删一个，
 * 少了两个」，很容易被当成删除逻辑写错。
 *
 * 这里把「取号」收敛成一个纯函数：给定当前时间，要么用时间戳，要么在上一号上加一。
 * 线程安全由调用方（Repository 的锁）负责，这样这段逻辑可以脱离 Android 单测。
 */
class IdSource(seed: Long = 0L) {

    private var last: Long = seed

    /** 从已有数据恢复，避免重启后从头开始、撞上历史 id。 */
    fun seed(value: Long) {
        if (value > last) last = value
    }

    fun next(now: Long): Long {
        last = if (now > last) now else last + 1
        return last
    }
}
