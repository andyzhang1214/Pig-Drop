package com.zhuzhu.sudi.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 找本机在局域网里的 IPv4 地址。
 *
 * 为什么不用 WifiManager：很多电视盒子是**有线**联网，甚至同时插着网线开着热点，
 * WifiManager 拿到的可能是那个没连外网的接口。这里直接遍历网卡，
 * 优先 wlan → eth → 其它，跳过回环和 169.254 这种自分配地址。
 */
object Lan {

    fun ipv4(): String? {
        val candidates = ArrayList<Pair<Int, String>>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null

        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            val priority = when {
                nif.name.startsWith("wlan") -> 0
                nif.name.startsWith("eth") -> 1
                nif.name.startsWith("ap") -> 3
                else -> 2
            }
            for (address in nif.inetAddresses) {
                if (address !is Inet4Address) continue
                if (address.isLoopbackAddress || address.isLinkLocalAddress) continue
                val host = address.hostAddress ?: continue
                candidates.add(priority to host)
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }

    fun baseUrl(port: Int): String {
        val ip = ipv4() ?: "127.0.0.1"
        return "http://$ip:$port"
    }
}
