package app.people.contacts.ui.organize

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.DuplicateSet
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.Avatar
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.LIST_AVATAR_SIZE
import app.people.contacts.util.formatNumber

/** Merge duplicates: one card per set of contacts that look like the same person. */
@Composable
fun MergeScreen(viewModel: ContactsViewModel, onBack: () -> Unit, onOpenContact: (Long) -> Unit) {
    val sets by viewModel.duplicates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Sets already sent to merge, hidden until the contacts reload without them.
    var pending by remember { mutableStateOf(emptySet<String>()) }
    val shown = sets.filter { it.key !in pending }
    var confirmAll by remember { mutableStateOf(false) }

    fun merge(toMerge: List<DuplicateSet>) {
        pending = pending + toMerge.map { it.key }
        viewModel.mergeDuplicateSets(toMerge) { merged ->
            val text = if (merged == toMerge.size) {
                context.resources.getQuantityString(R.plurals.merged, merged, merged)
            } else context.getString(R.string.merge_failed)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.merge_duplicates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (shown.size > 1) TextButton(onClick = { confirmAll = true }) { Text(stringResource(R.string.merge_all)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Outlined.AutoFixHigh, stringResource(R.string.merge_none_title), stringResource(R.string.merge_none_body))
                }
            } else {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.merge_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            items(shown, key = { it.key }) { set ->
                DuplicateCard(set, onOpenContact, onMerge = { merge(listOf(set)) }, onDismiss = { viewModel.dismissDuplicate(set) }, Modifier.animateItem())
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.merge_all_title)) },
            text = { Text(pluralStringResource(R.plurals.merge_all_body, shown.size, shown.size)) },
            confirmButton = { TextButton(onClick = { confirmAll = false; merge(shown) }) { Text(stringResource(R.string.merge_all)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DuplicateCard(
    set: DuplicateSet,
    onOpenContact: (Long) -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            set.contacts.forEach { contact ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenContact(contact.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(contact.name, contact.photoUri, contact.id, LIST_AVATAR_SIZE)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = contact.numbers.firstOrNull()?.let(::formatNumber) ?: contact.emails.firstOrNull()
                        if (detail != null) {
                            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                Button(onClick = onMerge) { Text(stringResource(R.string.merge)) }
            }
        }
    }
}
