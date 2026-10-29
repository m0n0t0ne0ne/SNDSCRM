package com.music.spotui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/*
 * Modern palette: the same black-and-green identity, with the flat #121212 greys replaced by
 * a slightly cool, layered set of surfaces. Each step up reads as "raised" through value, not
 * through borders, so cards and sheets separate cleanly without looking boxed in.
 */
val AppBackground: Color get() =
    if (app.myspoti.power.EnergySettings.oledBlack) Color.Black else Color(0xFF0E0E10)
val GridBackground: Color get() = tone(0xFF2A2A2A)
/** The accent (Spotify green, or the colour a supporter chose). */
val AppPalette: Color get() = AppAccent
val AppAccent: Color get() = com.music.spotui.data.api.SupportReminder.accentColor

/** Primary text and icons. */
val AppText: Color = Color(0xFFF5F5F7)
/** Secondary text and icons: a touch cooler and lighter than #B3B3B3, easier to read on the new surfaces. */
val AppMuted: Color = Color(0xFFA7A7AE)
/** Destructive actions (delete, leave, block) and failures shown as text. */
val AppDestructive = Color(0xFFF15E6C)
/** What a tint is mixed with to make a header. */
val AppShade: Color = Color.Black

/**
 * One of the palette's raised greys. Screens still ask for the classic Spotify values
 * (#181818 ... #3E3E3E); each is mapped here to its step in the modern surface scale, so the
 * whole app picks up the new surfaces from this one place. Unknown values pass through.
 */
fun tone(argb: Long): Color = Color(MODERN_TONES[argb] ?: argb)

private val MODERN_TONES: Map<Long, Long> = mapOf(
    0xFF121212 to 0xFF0E0E10, // page
    0xFF181818 to 0xFF151518,
    0xFF1A1A1A to 0xFF17171A,
    0xFF1E1E1E to 0xFF1B1B1F, // card
    0xFF1F1F1F to 0xFF1C1C20,
    0xFF242424 to 0xFF212125,
    0xFF252525 to 0xFF222226,
    0xFF282828 to 0xFF242428, // sheet
    0xFF2A2A2A to 0xFF26262B, // chip, tile
    0xFF333333 to 0xFF2E2E34,
    0xFF3A3A3A to 0xFF34343A,
    0xFF3E3E3E to 0xFF38383F, // divider, outline
    0xFF535353 to 0xFF4E4E56,
)

/** The cover's colour as a page header: darkened toward the page, so the title stays readable. */
fun headerTint(tint: Color, amount: Float): Color = lerp(tint, AppBackground, amount)

/** Corner radii, from list thumbnails up to sheets. */
object AppShapes {
    /** Track and queue thumbnails. */
    val Thumb: Shape = RoundedCornerShape(8.dp)
    /** Shortcut tiles, chips' squarer cousins, small cards. */
    val Tile: Shape = RoundedCornerShape(10.dp)
    /** Album, playlist and mix covers in carousels and headers. */
    val Cover: Shape = RoundedCornerShape(12.dp)
    /** The big cover in the full player. */
    val CoverLarge: Shape = RoundedCornerShape(18.dp)
    /** The floating mini player. */
    val MiniPlayer: Shape = RoundedCornerShape(14.dp)
    /** Bottom sheets and dialogs (top corners). */
    val Sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    /** Fully rounded: pills and chips. */
    val Pill: Shape = RoundedCornerShape(50)
}
