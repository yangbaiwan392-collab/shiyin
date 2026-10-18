package com.yangy.shiyin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.yangy.shiyin.data.model.BitDepth
import com.yangy.shiyin.data.model.OutputPath
import com.yangy.shiyin.data.model.SampleRate
import com.yangy.shiyin.data.repo.DarkMode
import com.yangy.shiyin.data.repo.ListDensity
import com.yangy.shiyin.data.repo.PlayerLayout
import com.yangy.shiyin.ui.theme.Skin

/** 10 段均衡器预设，单位 dB */
object EqPresets {
    val names = listOf("平坦", "人声", "低音", "古典", "摇滚", "爵士", "夜间", "便携")
    private val table = mapOf(
        "平坦" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "人声" to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
        "低音" to floatArrayOf(7f, 6f, 4f, 1f, 0f, 0f, 0f, 1f, 2f, 3f),
        "古典" to floatArrayOf(4f, 3f, 2f, 0f, 0f, 0f, 1f, 2f, 3f, 4f),
        "摇滚" to floatArrayOf(5f, 4f, 1f, -1f, -1f, 1f, 3f, 4f, 4f, 3f),
        "爵士" to floatArrayOf(4f, 3f, 1f, 2f, -1f, -1f, 0f, 2f, 3f, 4f),
        "夜间" to floatArrayOf(-3f, -2f, 0f, 2f, 3f, 3f, 2f, 0f, -2f, -4f),
        "便携" to floatArrayOf(5f, 4f, 0f, -2f, -2f, 0f, 3f, 4f, 4f, 3f),
    )

    fun gains(name: String): FloatArray = (table[name] ?: table["平坦"])!!.copyOf(10)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: AppViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val count by vm.trackCount.collectAsState()
    val scanState by vm.scanState.collectAsState()

    SettingsScaffold("设置", onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { GroupTitle("外观") }
            item { NavRow("皮肤与配色", "${Skin.fromId(settings.skinId).label} · ${settings.darkMode.label()}", onOpenSkins) }
            item { NavRow("音质与均衡器", settings.outputPath.label, onOpenSound) }
            item {
                ToggleRow("从封面取色（动态主题）", settings.dynamicFromCover) { value -> vm.update { s -> s.copy(dynamicFromCover = value) } }
            }
            item {
                ChoiceRow(
                    label = "列表密度",
                    options = ListDensity.entries.map { it.label },
                    selected = ListDensity.entries.indexOf(settings.listDensity),
                ) { index -> vm.update { it.copy(listDensity = ListDensity.entries[index]) } }
            }
            item {
                ChoiceRow(
                    label = "播放页版式",
                    options = PlayerLayout.entries.map { it.label },
                    selected = PlayerLayout.entries.indexOf(settings.playerLayout),
                ) { index -> vm.update { it.copy(playerLayout = PlayerLayout.entries[index]) } }
            }

            item { GroupTitle("音乐库") }
            item { NavRow("扫描目录管理", "已添加自定义目录", onOpenFolders) }
            item {
                ToggleRow("跳过短于 ${settings.scanMinSeconds} 秒的文件", settings.skipNotifications) { value -> vm.update { s -> s.copy(skipNotifications = value) } }
            }
            item {
                Text("最小秒数 ${settings.scanMinSeconds}s", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.scanMinSeconds.toFloat(),
                    onValueChange = { value -> vm.update { it.copy(scanMinSeconds = value.toInt()) } },
                    valueRange = 0f..120f,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("曲库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$count 首", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                if (scanState is com.yangy.shiyin.data.repo.ScanState.Running) {
                    ScanBanner(scanState as com.yangy.shiyin.data.repo.ScanState.Running)
                }
            }
            item {
                androidx.compose.material3.Button(
                    onClick = { vm.scan() },
                    enabled = scanState !is com.yangy.shiyin.data.repo.ScanState.Running,
                ) { Text(if (scanState is com.yangy.shiyin.data.repo.ScanState.Running) "扫描中…" else "重新扫描") }
            }

            item { GroupTitle("隐私与信息") }
            item { NavRow("关于拾音", "权限清单、隐私声明与依赖校验和", onOpenAbout) }
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("拾音不联网", fontWeight = FontWeight.SemiBold)
                    Text(
                        "APK 未声明 INTERNET 权限，系统在层面禁止本应用建立任何网络连接。封面、歌词、元数据全部来自音频文件本身，播放记录与设置只保存在本机。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(onBack: () -> Unit, vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()

    SettingsScaffold("音质", onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            GroupTitle("输出通路")
            OutputPath.entries.forEach { path ->
                RadioRow(
                    label = path.label,
                    detail = path.detail,
                    selected = settings.outputPath == path,
                ) {
                    vm.update { it.copy(outputPath = path) }
                }
            }
            if (!settings.outputPath.supportsEffects) {
                Notice("源码直通由硬件解码，均衡器、音量增强与限幅会失效。")
            }

            GroupTitle("解码输出参数")
            ChoiceRow(
                label = "采样率",
                options = SampleRate.entries.map { it.label },
                selected = SampleRate.entries.indexOf(settings.sampleRate),
            ) { index -> vm.update { it.copy(sampleRate = SampleRate.entries[index]) } }
            ChoiceRow(
                label = "位深",
                options = BitDepth.entries.map { it.label },
                selected = BitDepth.entries.indexOf(settings.bitDepth),
            ) { index -> vm.update { it.copy(bitDepth = BitDepth.entries[index]) } }
            Text(
                "这两项对 WMA/APE 等需转码的格式立即生效；对硬解格式，设备是否接受由系统决定，实际通路见播放页「文件信息」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GroupTitle("均衡器")
            ToggleRow("启用均衡器", settings.eqOn && settings.outputPath.supportsEffects, enabled = settings.outputPath.supportsEffects) { value -> vm.update { s -> s.copy(eqOn = value) } }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                items(EqPresets.names.size) { index ->
                    val name = EqPresets.names[index]
                    FilterChip(
                        selected = settings.eqPresetName == name,
                        onClick = { vm.update { it.copy(eqPresetName = name, eqGains = EqPresets.gains(name), eqOn = true) } },
                        label = { Text(name, fontSize = 13.sp) },
                    )
                }
            }
            EqGrid(
                gains = settings.eqGains,
                enabled = settings.eqOn && settings.outputPath.supportsEffects,
                onChange = { index, value ->
                    vm.update { current ->
                        val copy = current.eqGains.copyOf(10)
                        copy[index] = value
                        current.copy(eqGains = copy, eqPresetName = "自定义")
                    }
                },
            )

            GroupTitle("增益与保护")
            LabeledSlider(
                label = "预放大 ${settings.preampDb.toInt()} dB",
                value = settings.preampDb,
                range = -12f..12f,
            ) { value -> vm.update { s -> s.copy(preampDb = value) } }
            LabeledSlider(
                label = "音量增强 ${settings.volumeBoostPercent}%",
                value = settings.volumeBoostPercent.toFloat(),
                range = 0f..100f,
            ) { value -> vm.update { s -> s.copy(volumeBoostPercent = value.toInt()) } }
            ToggleRow("限幅器（防削波爆音）", settings.limiterOn) { value -> vm.update { s -> s.copy(limiterOn = value) } }

            GroupTitle("播放行为")

            LabeledSlider(
                label = "速度 ${settings.speed}x",
                value = settings.speed,
                range = 0.5f..2f,
            ) { value ->
                val rounded = (value * 100).roundToInt() / 100f
                vm.update { s -> s.copy(speed = rounded) }
                vm.player.setSpeed(rounded)
            }
            ToggleRow("单声道混合", settings.monoMix) { value -> vm.update { s -> s.copy(monoMix = value) } }
            ToggleRow("跳过静音段", settings.skipSilence) { value -> vm.update { s -> s.copy(skipSilence = value) } }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EqGrid(gains: FloatArray, enabled: Boolean, onChange: (Int, Float) -> Unit) {
    val labels = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { label ->
                Text(
                    label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label + "Hz",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(46.dp),
                )
                Slider(
                    value = gains[index],
                    onValueChange = { onChange(index, it) },
                    valueRange = -12f..12f,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%+.0f".format(gains[index]),
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(38.dp),
                )
            }
        }
    }
}



@Composable
fun SkinScreen(onBack: () -> Unit, vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    SettingsScaffold("皮肤", onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            GroupTitle("预设皮肤")
            Skin.entries.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { skin ->
                        SkinTile(
                            skin = skin,
                            selected = skin.id == settings.skinId,
                        ) { vm.update { it.copy(skinId = skin.id) } }
                    }
                }
            }
            GroupTitle("深色模式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.darkMode == mode,
                        onClick = { vm.update { it.copy(darkMode = mode) } },
                        label = { Text(mode.label(), fontSize = 13.sp) },
                    )
                }
            }
            GroupTitle("自定义强调色")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                ACCENTS.forEach { accent ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(
                                width = if (settings.customAccent == accent.toArgb()) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable {
                                vm.update { it.copy(customAccent = if (it.customAccent == accent.toArgb()) null else accent.toArgb()) }
                            },
                    )
                }
            }
            TextButton(onClick = { vm.update { it.copy(customAccent = null) } }) { Text("恢复皮肤默认配色") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private val ACCENTS = listOf(
    Color(0xFF1B6B5A), Color(0xFF2B3A67), Color(0xFF9A6B3F), Color(0xFFB5566A),
    Color(0xFF3E7C74), Color(0xFF6A3D7D), Color(0xFF2F5D8A), Color(0xFF4A4F52),
)

@Composable
private fun SkinTile(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(skin.lightSeed),
        ) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(skin.darkSeed),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(skin.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun FoldersScreen(onBack: () -> Unit, vm: AppViewModel = viewModel()) {
    val folders by vm.folders.collectAsState()
    SettingsScaffold("扫描目录", onBack) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text(
                "系统媒体库（Music 等）会自动纳入。下面添加的文件夹用于覆盖 MediaStore 没索引到的位置，例如 SD 卡子目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let { vm.addFolder(it) } }
            androidx.compose.material3.Button(onClick = { picker.launch(null) }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加文件夹")
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(folder.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (folder.enabled) "已启用 · 最少 ${folder.minSeconds}s" else "已停用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = folder.enabled, onCheckedChange = { vm.setFolderEnabled(folder.treeUri, it) })
                        IconButton(onClick = { vm.removeFolder(folder.treeUri) }) {
                            Icon(Icons.Rounded.Delete, "移除")
                        }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun RadioRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selected) { options.first() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(vertical = 6.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun DarkMode.label(): String = when (this) {
    DarkMode.FOLLOW_SYSTEM -> "跟随系统"
    DarkMode.LIGHT -> "始终浅色"
    DarkMode.DARK -> "始终深色"
}
