package com.vayunmathur.contacts.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.library.ui.IconSettings
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.R as UiR
import com.vayunmathur.library.ui.Surface
import com.vayunmathur.library.ui.Text

/** Height of the "no labels" placeholder, so the Fix & manage section stays on screen below it. */
internal val EMPTY_LABELS_HEIGHT = 240.dp

/** A section heading on the Organize tab ("Labels", "Fix & manage"). */
@Composable
internal fun OrganizeSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The Organize tab's way into Settings, which is a page rather than a bottom tab. */
@Composable
internal fun OrganizeSettingsRow(onOpenSettings: () -> Unit) {
    Surface(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        ListItem(
            supportingContent = { Text(stringResource(R.string.settings_summary)) },
            leadingContent = { IconSettings() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(UiR.string.settings), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
