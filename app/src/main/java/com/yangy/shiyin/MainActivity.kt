package com.yangy.shiyin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yangy.shiyin.ui.AboutScreen
import com.yangy.shiyin.ui.AppViewModel
import com.yangy.shiyin.ui.FoldersScreen
import com.yangy.shiyin.ui.HomeScreen
import com.yangy.shiyin.ui.PermissionGate
import com.yangy.shiyin.ui.PlaylistScreen
import com.yangy.shiyin.ui.SearchScreen
import com.yangy.shiyin.ui.SettingsScreen
import com.yangy.shiyin.ui.SkinScreen
import com.yangy.shiyin.ui.SoundScreen
import com.yangy.shiyin.ui.theme.ShiyinTheme
import com.yangy.shiyin.ui.theme.Skin

sealed class Screen {
    data object Home : Screen()
    data object Player : Screen()
    data object Settings : Screen()
    data object Sound : Screen()
    data object Skins : Screen()
    data object Folders : Screen()
    data object Search : Screen()
    data object About : Screen()
    data class Playlist(val id: Long, val name: String) : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }
}

@Composable
fun Root() {
    val vm: AppViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 皮肤优先；只有用户显式开启“从封面取色”时才用封面主色覆盖强调色
    val coverSeed by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.Color?>(
        null,
        playerState.current?.uri,
        settings.dynamicFromCover,
    ) {
        value = if (!settings.dynamicFromCover) {
            null
        } else {
            val track = playerState.current
            val bitmap = if (track == null) null else com.yangy.shiyin.data.source.CoverArt.load(context, track)
            bitmap?.let { com.yangy.shiyin.ui.CoverColor.seedFrom(it) }
        }
    }

    ShiyinTheme(
        skin = Skin.fromId(settings.skinId),
        darkMode = settings.darkMode,
        dynamicFromCover = settings.dynamicFromCover,
        coverSeed = coverSeed,
        customAccent = settings.customAccent?.let { androidx.compose.ui.graphics.Color(it) },
    ) {
        Content(vm)
    }
}

@Composable
private fun Content(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val granted = remember { mutableStateOf(false) }

    val permissions = remember {
        buildList {
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val appContext = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        granted.value = results.values.any { it } || hasAudioPermission(appContext)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        granted.value = hasAudioPermission(appContext)
        if (!granted.value) launcher.launch(permissions.toTypedArray())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (!granted.value) {
            PermissionGate { launcher.launch(permissions.toTypedArray()) }
            return@Surface
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + androidx.compose.animation.fadeIn())
                    .togetherWith(slideOutHorizontally { it / 4 } + androidx.compose.animation.fadeOut())
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Home -> HomeScreen(
                    onOpenPlayer = { screen = Screen.Player },
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenSearch = { screen = Screen.Search },
                    onOpenPlaylist = { id, name -> screen = Screen.Playlist(id, name) },
                    vm = vm,
                )
                Screen.Player -> PlayerHost(onBack = { screen = Screen.Home }, onOpenSound = { screen = Screen.Sound }, vm = vm)
                Screen.Settings -> SettingsScreen(
                    onBack = { screen = Screen.Home },
                    onOpenSound = { screen = Screen.Sound },
                    onOpenSkins = { screen = Screen.Skins },
                    onOpenFolders = { screen = Screen.Folders },
                    onOpenAbout = { screen = Screen.About },
                    vm = vm,
                )
                Screen.Sound -> SoundScreen(onBack = { screen = Screen.Settings }, vm = vm)
                Screen.Skins -> SkinScreen(onBack = { screen = Screen.Settings }, vm = vm)
                Screen.Folders -> FoldersScreen(onBack = { screen = Screen.Settings }, vm = vm)
                Screen.Search -> SearchScreen(onBack = { screen = Screen.Home }, vm = vm)
                Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
                is Screen.Playlist -> PlaylistScreen(
                    playlistId = target.id,
                    name = target.name,
                    onBack = { screen = Screen.Home },
                    vm = vm,
                )
            }
        }
    }
}

@Composable
private fun PlayerHost(onBack: () -> Unit, onOpenSound: () -> Unit, vm: AppViewModel) {
    com.yangy.shiyin.ui.PlayerScreen(onBack = onBack, onOpenSound = onOpenSound, vm = vm)
}

private fun hasAudioPermission(context: android.content.Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
