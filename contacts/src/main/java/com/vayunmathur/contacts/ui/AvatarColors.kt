package com.vayunmathur.contacts.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vayunmathur.library.ui.MaterialTheme

/** Background and letter colour for a contact's initial avatar. */
data class AvatarColors(val container: Color, val content: Color)

/**
 * Tonal avatar colours drawn from the wallpaper palette, like Google Contacts' letter avatars:
 * a container role with its matching "on" colour for the letter, so every avatar harmonises
 * with the theme in light and dark mode. Stable per contact id.
 */
@Composable
fun avatarColorsFor(id: Long): AvatarColors {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        AvatarColors(scheme.primaryContainer, scheme.onPrimaryContainer),
        AvatarColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
        AvatarColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
    )
    return palette[Math.floorMod(id, palette.size.toLong()).toInt()]
}
