package com.vayunmathur.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.Route
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.DuplicateSet
import com.vayunmathur.contacts.data.primaryOf
import com.vayunmathur.contacts.util.ContactViewModel
import com.vayunmathur.contacts.util.dismissDuplicates
import com.vayunmathur.contacts.util.mergeDuplicates
import com.vayunmathur.library.ui.AlertDialog
import com.vayunmathur.library.ui.Button
import com.vayunmathur.library.ui.DetailLazyColumn
import com.vayunmathur.library.ui.EmptyState
import com.vayunmathur.library.ui.IconMergeDuplicates
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.R as UiR
import com.vayunmathur.library.ui.Surface
import com.vayunmathur.library.ui.Text
import com.vayunmathur.library.ui.TextButton
import com.vayunmathur.library.ui.appBarScrollBehavior
import com.vayunmathur.library.util.NavBackStack

/**
 * Organize → Merge duplicates: one card per set of contacts that look like the same person,
 * each with Merge and Dismiss, plus Merge all in the top bar.
 */
@Composable
fun MergeDuplicatesPage(viewModel: ContactViewModel, backStack: NavBackStack<Route>) {
    val sets by viewModel.duplicateSets.collectAsStateWithLifecycle()
    // Cards already sent to merge, hidden until the provider re-sync drops them for real.
    var pending by remember { mutableStateOf(emptySet<String>()) }
    val shown = sets.filter { it.key !in pending }
    var confirmMergeAll by remember { mutableStateOf(false) }

    fun merge(toMerge: List<DuplicateSet>) {
        pending = pending + toMerge.map { it.key }
        viewModel.mergeDuplicates(toMerge)
    }

    DetailLazyColumn(
        title = stringResource(R.string.merge_duplicates),
        onNavigateBack = { backStack.pop() },
        actions = {
            if (shown.size > 1) {
                TextButton(onClick = { confirmMergeAll = true }) { Text(stringResource(R.string.merge_all)) }
            }
        },
        scrollBehavior = appBarScrollBehavior(),
    ) {
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.no_duplicates_title),
                    modifier = Modifier.fillParentMaxHeight(EMPTY_FRACTION),
                    message = stringResource(R.string.no_duplicates_message),
                    icon = { IconMergeDuplicates() },
                )
            }
        } else {
            item(key = "intro") {
                Text(
                    stringResource(R.string.merge_duplicates_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
            items(shown, key = { it.key }) { set ->
                DuplicateSetCard(
                    set = set,
                    decodePhoto = viewModel::decodePhoto,
                    onOpen = { backStack.add(Route.ContactDetail(it.id)) },
                    onMerge = { merge(listOf(set)) },
                    onDismiss = { viewModel.dismissDuplicates(set) },
                )
            }
        }
    }

    if (confirmMergeAll) {
        AlertDialog(
            onDismissRequest = { confirmMergeAll = false },
            title = { Text(stringResource(R.string.merge_all_title)) },
            text = { Text(pluralStringResource(R.plurals.merge_all_message, shown.size, shown.size)) },
            confirmButton = {
                TextButton(onClick = { merge(shown); confirmMergeAll = false }) {
                    Text(stringResource(R.string.merge_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMergeAll = false }) { Text(stringResource(UiR.string.cancel)) }
            },
        )
    }
}

private const val EMPTY_FRACTION = 0.7f

@Composable
private fun DuplicateSetCard(
    set: DuplicateSet,
    decodePhoto: (String) -> android.graphics.Bitmap?,
    onOpen: (Contact) -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = remember(set) { primaryOf(set) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            set.contacts.forEach { contact -> DuplicateRow(contact, decodePhoto) { onOpen(contact) } }
            Text(
                stringResource(R.string.merge_keeps, primary.name.value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.dismiss)) }
                Button(onClick = onMerge) { Text(stringResource(R.string.merge)) }
            }
        }
    }
}

/** One contact in a set: photo, name, and the number or email that most likely matched. */
@Composable
private fun DuplicateRow(contact: Contact, decodePhoto: (String) -> android.graphics.Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(contact = contact, decodePhoto = decodePhoto, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name.value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val secondary = contact.details.phoneNumbers.firstOrNull { it.number.isNotBlank() }?.number
                ?: contact.details.emails.firstOrNull { it.address.isNotBlank() }?.address
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
