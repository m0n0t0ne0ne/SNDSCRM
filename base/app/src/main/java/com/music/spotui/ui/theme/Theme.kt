package com.music.spotui.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The modernized dark palette, so the Material components that read the scheme (switches,
 * sliders, dialogs, spinners, text fields, menus) match the screens: accent green on a
 * near-black page, the cool layered surfaces from [tone], and no tonal tint on elevated surfaces.
 */
private fun spotifyColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = accent,
    onPrimaryContainer = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = tone(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    tertiary = accent,
    onTertiary = Color.Black,
    background = AppBackground,
    onBackground = AppText,
    surface = AppBackground,
    onSurface = AppText,
    surfaceVariant = tone(0xFF2A2A2A),
    onSurfaceVariant = AppMuted,
    surfaceTint = Color.Transparent,
    surfaceDim = AppBackground,
    surfaceBright = tone(0xFF2A2A2A),
    surfaceContainerLowest = AppBackground,
    surfaceContainerLow = tone(0xFF181818),
    surfaceContainer = tone(0xFF1F1F1F),
    surfaceContainerHigh = tone(0xFF282828),
    surfaceContainerHighest = tone(0xFF3E3E3E),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = accent,
    outline = Color(0xFF6E6E78),
    outlineVariant = tone(0xFF3E3E3E),
    error = Color(0xFFF3727F),
    onError = Color.Black,
    scrim = Color.Black,
)

@Composable
fun SpotuiTheme(
    content: @Composable () -> Unit
) {
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    val policy = energy.policy
    androidx.compose.runtime.LaunchedEffect(policy.videoMaxHeight, policy.allowBackgroundLookups) {
        com.music.spotui.video.MusicVideoController.onEnergyPolicyChanged()
    }
    // Dark only, like Spotify: a saved choice from the withdrawn light palette is ignored.
    val visualScheme: ColorScheme = spotifyColorScheme(AppAccent)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.attributes = window.attributes.apply { preferredRefreshRate = policy.displayRefreshCap }
            window.statusBarColor = Color.Transparent.toArgb()
            // White icons on the dark palette.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            // B19: the navigation bar was left to the system — a white band under the always-dark
            // UI (reported in the modal sheets, where the bar was opaque) with dark icons on it.
            window.navigationBarColor = Color.Transparent.toArgb()
            // Without this Android lays its own translucent scrim on a transparent bar (API 29+),
            // which showed as a differently coloured strip under the full-screen Canvas.
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            // Behind the first frame and under overscroll: the palette's page colour.
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(AppBackground.toArgb()))
        }
    }

    MaterialTheme(
        colorScheme = visualScheme,
        typography = Typography,
        // Rounder Material components (dialogs, menus, text fields, cards) to match the screens.
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        ),
    ) {
        // Bare Text() uses LocalTextStyle, which MaterialTheme does NOT derive
        // from the typography — so provide Montserrat as the global default so
        // every screen picks it up without touching each Text call.
        androidx.compose.material3.ProvideTextStyle(
            value = androidx.compose.material3.LocalTextStyle.current.copy(
                fontFamily = Montserrat,
                letterSpacing = (-0.2).sp,
            ),
            content = content,
        )
    }
}
