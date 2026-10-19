package com.yangy.shiyin.data.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 联网补全（歌词/封面）前的网络门槛。
 *
 * 规则只有一条：**只在 WiFi / 以太网下才允许发请求**，移动数据一律不联网。
 *
 * 所以界面上只有一个开关「联网补全歌词与封面（仅 WiFi）」——
 * 打开就是"WiFi 下才补"，关掉就是"完全不补"，没有第二种组合需要用户去理解。
 *
 * 判断失败一律返回 false —— 宁可这次不联网，也不要偷偷用流量。
 */
object NetworkState {

    fun canFetch(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)
}
