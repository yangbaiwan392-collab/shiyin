package com.yangy.shiyin.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yangy.shiyin.BuildConfig

private enum class PermKind(val label: String) {
    RUNTIME("需你同意"),
    INSTALL("安装时自动授予"),
    NONE("未申请"),
}

private data class PermissionEntry(
    val name: String,
    val title: String,
    val reason: String,
    val kind: PermKind,
    val maxSdk: Int? = null,
)

private val DECLARED = listOf(
    PermissionEntry(
        name = "android.permission.READ_MEDIA_AUDIO",
        title = "读取音频文件",
        reason = "扫描并播放你手机里的音乐。Android 13 及以上使用这一项，不读取照片和视频。",
        kind = PermKind.RUNTIME,
    ),
    PermissionEntry(
        name = "android.permission.READ_EXTERNAL_STORAGE",
        title = "读取外部存储（旧系统）",
        reason = "仅在 Android 12L 及以下用于读取音频，新版本系统不会申请。",
        kind = PermKind.RUNTIME,
        maxSdk = 32,
    ),
    PermissionEntry(
        name = "android.permission.POST_NOTIFICATIONS",
        title = "显示通知",
        reason = "锁屏与通知栏的播放控制。关闭后仍可播放，只是看不到控制条。",
        kind = PermKind.RUNTIME,
    ),
    PermissionEntry(
        name = "android.permission.FOREGROUND_SERVICE",
        title = "前台服务",
        reason = "让播放能在你切到别的界面后继续，不被系统回收。",
        kind = PermKind.INSTALL,
    ),
    PermissionEntry(
        name = "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        title = "媒体播放前台服务",
        reason = "上面那项在 Android 14 之后要求的具体类型声明。",
        kind = PermKind.INSTALL,
    ),
    PermissionEntry(
        name = "android.permission.WAKE_LOCK",
        title = "保持唤醒",
        reason = "锁屏后继续解码音频，避免系统休眠导致播放中断。",
        kind = PermKind.INSTALL,
    ),
    PermissionEntry(
        name = "android.permission.MODIFY_AUDIO_SETTINGS",
        title = "调整音频设置",
        reason = "均衡器、限幅器与输出通路切换需要改动音频处理链。",
        kind = PermKind.INSTALL,
    ),
    PermissionEntry(
        name = "android.permission.BLUETOOTH_CONNECT",
        title = "蓝牙连接",
        reason = "读取蓝牙耳机/车机的 AVRCP 元数据并响应线控。不用于扫描设备。",
        kind = PermKind.RUNTIME,
    ),
    PermissionEntry(
        name = "android.permission.INTERNET",
        title = "访问网络（仅歌词）",
        reason = "只在设置里打开「联网查找歌词」后才会用，用于按歌名查一次歌词；关闭时不发出任何请求。",
        kind = PermKind.INSTALL,
    ),
)



@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val states = remember(context) { DECLARED.map { it.name to grantLabel(context, it) } }.toMap()

    SettingsScaffold("关于拾音", onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            GroupTitle("版本")
            Text("拾音 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodyMedium)
            Text("本地无广告音乐播放器", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            GroupTitle("联网说明")
            Body("""拾音是本地播放器：扫描、播放、均衡器、歌单全部在本机完成，不需要联网，也没有任何广告、账号或统计。

唯一会联网的地方是「歌词与封面」：默认关闭。只有你在设置里手动打开「联网补全歌词与封面」之后，遇到本地没有歌词或没有封面的歌曲时，才会把「歌名 + 艺术家」发出去查一次；查到的东西会立刻存成本地文件，之后永不重复请求。除此之外不上传任何东西——不上传播放记录、不上传播放列表、不上传设备信息。""")

            GroupTitle("权限清单")
            DECLARED.forEach { entry ->
                PermissionCard(
                    entry = entry,
                    status = states[entry.name] ?: "未知",
                )
            }

            GroupTitle("未申请的权限")
            NotRequestedCard()

            GroupTitle("第三方组件与来源校验")
            Body("""Media3 / ExoPlayer 1.11.1、Jetpack Compose、Room、DataStore 均来自 Google 与 Maven Central 官方仓库。全部为 JVM 依赖，不含任何原生解码库 —— APK 里只有 MediaCodec 这一条系统解码通路。""")

            GroupTitle("致谢")
            Body("""致谢 GD 音乐提供歌词与封面。""")

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermissionCard(entry: PermissionEntry, status: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(status)
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.name + (entry.maxSdk?.let { "（仅 ≤ Android $it）" } ?: "") + " · " + entry.kind.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NotRequestedCard() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("定位与 WiFi 信息", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip("未申请")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "这两个权限用不到任何功能，清单合并阶段会被强制移除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ACCESS_FINE_LOCATION · ACCESS_COARSE_LOCATION · ACCESS_WIFI_STATE —— 全部未申请",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    val granted = text == "已授予"
    Surface(
        color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

private fun grantLabel(context: Context, entry: PermissionEntry): String {
    if (entry.kind == PermKind.INSTALL) return "已授予"
    if (entry.maxSdk != null && Build.VERSION.SDK_INT > entry.maxSdk) return "当前系统不需要"
    return if (ContextCompat.checkSelfPermission(context, entry.name) == PackageManager.PERMISSION_GRANTED) {
        "已授予"
    } else {
        "未授予"
    }
}
