package com.example.plantcare.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.plantcare.R

/**
 * PlantCare — Compose typography (G-CRITICAL-1, 2026-05-17).
 *
 * Pre-fix this file used FontFamily.SansSerif (= Android default Roboto)
 * for h5/body1/caption and left every other style on Material defaults.
 * That meant the entire Compose layer (Home / WeekBar / RemindersList /
 * CalendarPhotoGrid / MonthPicker) rendered in Roboto while the XML layer
 * used Manrope ExtraBold + Inter — two different typefaces in one app,
 * trivially visible on Home where the WeekBar (Compose) sat under a
 * MaterialToolbar (XML) showing "Home".
 *
 * Now: Compose pulls the same two font families from `res/font/` that
 * the XML themes.xml uses. Typography is fully expanded so every
 * MaterialTheme.typography.* slot maps to an explicit Inter weight.
 *
 * Family mapping (matches XML themes.xml decisions):
 *   • Inter  Regular  (400) — body, caption, day-strip numbers, lists
 *   • Inter  SemiBold (600) — titles, buttons, subtitles, headers
 *   • Manrope ExtraBold (800) — reserved for the brand-hero h5/headline
 *
 * Reference apps (Plant Identifier, Set care reminders) use Inter-style
 * Regular for body and SemiBold for headings — this matches. Manrope
 * stays for the one moment where the brand mark really speaks (Home's
 * top heading).
 */

private val InterFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

private val ManropeFamily = FontFamily(
    Font(R.font.manrope_extra_bold, FontWeight.ExtraBold)
)

val Typography = Typography(
    // Brand-hero. Used by big screen titles like Home's "Home".
    h5 = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.015).sp
    ),
    // Card / dialog / section titles. Pre-fix: Manrope ExtraBold-with-textStyle-bold.
    // Now: Inter SemiBold to feel premium without "shouty".
    h6 = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.005).sp
    ),
    subtitle1 = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp
    ),
    subtitle2 = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    body1 = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    body2 = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    button = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.sp
    ),
    caption = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    overline = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)
