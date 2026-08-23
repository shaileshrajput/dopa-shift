package com.dopashift.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

/**
 * DopaShift Kinetic Design System — Typography
 *
 * - Hanken Grotesk (Headlines): Sharp, professional grotesque for scores and goal titles
 * - Inter (Body): Gold standard for UI legibility, used for descriptions and task lists
 * - JetBrains Mono (Labels/Metadata): Precision feel for timestamps, metrics, and logs
 */

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.dopashift.ui.R.array.com_google_android_gms_fonts_certs
)

// === Font Families ===

private val HankenGroteskFont = GoogleFont("Hanken Grotesk")
private val InterFont = GoogleFont("Inter")
private val JetBrainsMonoFont = GoogleFont("JetBrains Mono")

val HankenGroteskFamily = FontFamily(
    Font(googleFont = HankenGroteskFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = HankenGroteskFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = HankenGroteskFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

val InterFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
)

// === M3 Typography Scale ===

val DopaShiftTypography = Typography(
    // Display Large — Hanken Grotesk 57sp Bold
    displayLarge = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    // Display Medium — Hanken Grotesk 45sp Bold
    displayMedium = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    // Display Small — Hanken Grotesk 36sp SemiBold
    displaySmall = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    // Headline Large — Hanken Grotesk 32sp SemiBold
    headlineLarge = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    // Headline Medium — Hanken Grotesk 28sp SemiBold
    headlineMedium = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    // Headline Small — Hanken Grotesk 24sp SemiBold
    headlineSmall = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    // Title Large — Hanken Grotesk 22sp Medium
    titleLarge = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // Title Medium — Inter 16sp Medium
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    // Title Small — Inter 14sp Medium
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Body Large — Inter 16sp Regular
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    // Body Medium — Inter 14sp Regular
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    // Body Small — Inter 12sp Regular
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Label Large — JetBrains Mono 14sp Medium
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Label Medium — JetBrains Mono 12sp Medium
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    // Label Small — JetBrains Mono 11sp Medium
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
