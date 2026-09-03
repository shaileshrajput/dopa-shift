package com.dopashift.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * DopaShift Kinetic Design System — Typography
 *
 * - Hanken Grotesk (display/headline/title): Sharp, professional grotesque for scores and goal titles
 * - Inter (body/label): Gold standard for UI legibility, used for descriptions and task lists
 * - JetBrains Mono (metric/counter/badge): Precision feel for timestamps, metrics, and logs
 *
 * The three families below are the bundled font resources for the app (registered here via the
 * downloadable Google Fonts provider, the project's font-registration mechanism — see
 * `font_certs.xml`). [DopaShiftTokens.TextRoles] builds its named [androidx.compose.ui.text.TextStyle]
 * roles over these same families, and [DopaShiftTypography] maps those token roles onto the Material 3
 * [Typography] slots so screens read type by M3 slot without inlining raw size/weight literals
 * (AUI-8.1, AUI-8.4).
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

/**
 * Material 3 [Typography] for the DopaShift Kinetic system, assembled *entirely* from the named
 * roles in [DopaShiftTokens.TextRoles] — no size / weight / line-height / letter-spacing literals
 * live here (those are defined once in the token file, AUI-8.1).
 *
 * Slot assignment follows the design's role-to-family contract (AUI-8.4):
 *  - **display / headline / title** slots → Hanken Grotesk roles (`displayLarge`, `headlineLarge`,
 *    `headlineMedium`, `titleMedium`).
 *  - **body / label** slots → Inter roles (`bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`).
 *  - **metric / badge readouts** → JetBrains Mono roles (`metricDisplay`, `metricCounter`,
 *    `labelMono`), surfaced through the `label*` slots so numeric UI reads in the monospace family
 *    and avoids layout jitter as values tick.
 *
 * The token set names 13 roles against 15 M3 slots; slots without a distinct token role reuse the
 * closest-scale role so the whole M3 scale resolves to DopaShift type.
 */
val DopaShiftTypography = Typography(
    // Display — Hanken Grotesk (display roles)
    displayLarge = DopaShiftTokens.TextRoles.displayLarge,
    displayMedium = DopaShiftTokens.TextRoles.displayLargeMobile,
    displaySmall = DopaShiftTokens.TextRoles.headlineLarge,
    // Headline — Hanken Grotesk (headline roles)
    headlineLarge = DopaShiftTokens.TextRoles.headlineLarge,
    headlineMedium = DopaShiftTokens.TextRoles.headlineMedium,
    headlineSmall = DopaShiftTokens.TextRoles.headlineMedium,
    // Title — Hanken Grotesk (title role)
    titleLarge = DopaShiftTokens.TextRoles.headlineMedium,
    titleMedium = DopaShiftTokens.TextRoles.titleMedium,
    titleSmall = DopaShiftTokens.TextRoles.titleMedium,
    // Body — Inter (body roles)
    bodyLarge = DopaShiftTokens.TextRoles.bodyLarge,
    bodyMedium = DopaShiftTokens.TextRoles.bodyMedium,
    bodySmall = DopaShiftTokens.TextRoles.bodySmall,
    // Label — Inter (label) + JetBrains Mono (metric/badge readout)
    labelLarge = DopaShiftTokens.TextRoles.labelLarge,
    labelMedium = DopaShiftTokens.TextRoles.metricCounter,
    labelSmall = DopaShiftTokens.TextRoles.labelMono,
)
