package com.music.spotui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R

/**
 * UI typeface: Manrope (SIL Open Font License 1.1, licenses/Manrope-OFL.txt), a free
 * geometric sans close to the official app's look. One variable font file, each weight
 * picked through its "wght" axis (200 to 800: the heaviest cut asks for 900 and gets 800).
 * It has Latin, Cyrillic and Greek glyphs. The Figtree it replaced had no Cyrillic, so Android
 * drew those letters with the system font and a Russian or Ukrainian interface jumped between
 * two typefaces (issue #62).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sans(weight: Int) = Font(
    R.font.app_sans,
    weight = FontWeight(weight),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(weight),
    ),
)

val AppSans = FontFamily(sans(400), sans(500), sans(600), sans(700), sans(800), sans(900))
/** Headings: the same family, heavier cuts. */
val AppSansTitle = AppSans

// Kept as an alias so existing references (Theme.kt etc.) don't break.
val Montserrat = AppSans

private val base = TextStyle(fontFamily = AppSans)
private val title = TextStyle(fontFamily = AppSansTitle, fontWeight = FontWeight.ExtraBold)

/*
 * A real scale instead of one size for everything: big, tight display titles for page headers,
 * firm section titles, and comfortable body text. Negative tracking on the large sizes keeps
 * heavy Manrope from looking loose.
 */
val Typography = Typography(
    displayLarge = title.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.6).sp),
    displayMedium = title.copy(fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp),
    displaySmall = title.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.9).sp),
    headlineLarge = title.copy(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp),
    headlineMedium = title.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
    headlineSmall = title.copy(fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

/** Named styles the classic screens use directly, so they share one scale. */
object AppType {
    /** "Good evening", page titles in the top bar. */
    val Greeting = title.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp)
    /** Home and artist section titles ("Made for you", "Popular"). */
    val Section = title.copy(fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.55).sp)
    /** Album, playlist and artist names in the page header. */
    val PageTitle = title.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.0).sp)
    /** Artist name over the hero image. */
    val Hero = title.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.8).sp)
    /** Song title in the full player. */
    val PlayerTitle = title.copy(fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.6).sp)
    /** Card and row titles. */
    val ItemTitle = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp)
    /** Card and row subtitles. */
    val ItemSub = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    /** Small uppercase labels ("ALBUM", "PLAYING FROM"). */
    val Eyebrow = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}
