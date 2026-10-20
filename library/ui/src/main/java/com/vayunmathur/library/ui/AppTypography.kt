package com.vayunmathur.library.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Google Sans Flex, the typeface Google's own apps use, bundled unmodified from
 * github.com/google/fonts under the SIL Open Font License (see
 * assets/licenses/google_sans_flex_OFL.txt).
 *
 * It is one variable font file; each weight below is the same file with its `wght` axis set,
 * which `Font(resId, weight)` does by default. Scripts it does not cover (Arabic, CJK, ...)
 * fall back to the system font per glyph.
 */
internal val GoogleSansFlex: FontFamily = FontFamily(
    listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    ).map { weight -> Font(R.font.google_sans_flex, weight) }
)

/** Material's type scale with every style set in [family]. */
internal fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
