package com.vayunmathur.contacts.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.library.ui.IconMergeDuplicates
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
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The Organize tab's way into Settings, which is a page rather than a bottom tab. */
@Composable
internal fun OrganizeSettingsRow(onOpenSettings: () -> Unit) {
    Surface(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        ListItem(
            supportingContent = {
                Text(
                    stringResource(R.string.settings_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = { IconSettings(tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(UiR.string.settings), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Organize's way into Merge duplicates, with how many suggestions there are. */
@Composable
internal fun OrganizeMergeRow(suggestions: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        ListItem(
            supportingContent = {
                Text(
                    if (suggestions > 0) {
                        pluralStringResource(R.plurals.merge_duplicates_summary, suggestions, suggestions)
                    } else {
                        stringResource(R.string.merge_duplicates_none)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (suggestions > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = { IconMergeDuplicates(tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(R.string.merge_duplicates), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * The summary card at the top of Organize, after Google's account card: a large count of the
 * contacts in visible accounts.
 */
@Composable
internal fun ContactCountCard(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = pluralStringResource(R.plurals.contacts_in_address_book, count),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
