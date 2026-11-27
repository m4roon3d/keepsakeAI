package com.keepsakeai.app.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.R

/** Plus Jakarta Sans for headings and big numbers: rounder and with more character than the body face. */
val Display = FontFamily(
    Font(R.font.jakarta_semibold, FontWeight.Normal),
    Font(R.font.jakarta_semibold, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

/** Inter for everything you read: clear at small sizes, with even-width digits for money. */
val Body = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.ExtraBold),
)

/**
 * The type scale. Every size in the app is one of these, so the same kind of text looks the same everywhere,
 * and each step is far enough from the next to read as a different level.
 */
object Ts {
    val caption = 11.sp   // tiny uppercase labels, badges
    val meta = 12.sp      // dates, counts, secondary lines
    val body = 14.sp      // descriptions and most reading text
    val title = 15.sp     // card titles, list item names
    val lead = 17.sp      // card headlines, key numbers
    val section = 20.sp   // section headers inside a screen
    val heading = 22.sp
    val screen = 26.sp    // screen titles
    val hero = 32.sp      // the one big number on a screen
}

/** Spacing steps on a 4 dp grid. */
object Gap {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

private val base = TextStyle(
    fontFamily = Body,
    lineHeight = 1.42.em,                  // follows the font size, so wrapped small text never feels cramped
    fontFeatureSettings = "tnum, cv11",    // even-width digits so amounts line up; simpler single-storey 'a'
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun display(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, tracking: Double) =
    base.copy(fontFamily = Display, fontSize = size, fontWeight = weight, letterSpacing = tracking.sp, lineHeight = 1.2.em)

internal val TroveTypography = androidx.compose.material3.Typography(
    displayLarge = display(Ts.hero, FontWeight.ExtraBold, -0.8),
    displayMedium = display(30.sp, FontWeight.ExtraBold, -0.7),
    displaySmall = display(28.sp, FontWeight.ExtraBold, -0.6),
    headlineLarge = display(Ts.screen, FontWeight.ExtraBold, -0.5),
    headlineMedium = display(Ts.screen, FontWeight.ExtraBold, -0.5),
    headlineSmall = display(Ts.heading, FontWeight.Bold, -0.4),
    titleLarge = display(Ts.section, FontWeight.Bold, -0.3),
    titleMedium = base.copy(fontSize = Ts.lead, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall = base.copy(fontSize = Ts.title, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = Ts.body),
    bodySmall = base.copy(fontSize = Ts.meta),
    labelLarge = base.copy(fontSize = Ts.body, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = Ts.meta, fontWeight = FontWeight.SemiBold),
    labelSmall = base.copy(fontSize = Ts.caption, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
)
