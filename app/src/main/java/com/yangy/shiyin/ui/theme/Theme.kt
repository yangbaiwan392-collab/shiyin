package com.yangy.shiyin.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yangy.shiyin.data.repo.DarkMode

@Composable
fun ShiyinTheme(
    skin: Skin = Skin.MOSS,
    /** 自定义皮肤优先于内置皮肤；为空则用内置的那套 */
    customSkin: CustomSkin? = null,
    darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    dynamicFromCover: Boolean = false,
    coverSeed: Color? = null,
    customAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    // 自定义皮肤优先；否则用内置枚举
    val fallback = customSkin?.accent(dark) ?: if (dark) skin.darkSeed else skin.lightSeed

    val scheme = when {
        customAccent != null -> schemeFor(customAccent, dark)
        dynamicFromCover && coverSeed != null -> schemeFor(coverSeed, dark)
        else -> schemeFor(fallback, dark)
    }

    val surface = customSkin?.surface(dark) ?: if (dark) skin.darkSurface else skin.lightSurface
    // 皮肤始终决定表面色与层级；封面取色/自定义色只替换强调色，不覆盖皮肤基调
    val finalScheme = scheme.copy(background = surface, surface = surface)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = ShiyinTypography,
        shapes = ShiyinShapes(customSkin?.roundDp ?: skin.roundDp),
        content = content,
    )
}

private fun schemeFor(seed: Color, dark: Boolean) =
    if (dark) darkColorScheme(primary = seed, secondary = seed.copy(alpha = 0.8f))
    else lightColorScheme(primary = seed, secondary = seed.copy(alpha = 0.8f))
