package app.people.contacts.ui.organize

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.people.contacts.data.ContactSummary
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ContactRow
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.TextInputDialog
import app.people.contacts.ui.components.segmentShape
import kotlinx.coroutines.launch

/** One label: its contacts, adding and removing them, and renaming or deleting the label. */
@Composable
fun LabelScreen(viewModel: ContactsViewModel, id: Long, onBack: () -> Unit, onOpenContact: (Long) -> Unit) {
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val label = labels?.firstOrNull { it.id == id }
    LaunchedEffect(labels) { if (labels != null && label == null) onBack() }
    if (label == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val members = contacts.orEmpty().filter { it.id in label.memberIds }
    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    if (picking) {
        ContactPicker(
            contacts = contacts.orEmpty().filter { it.id !in label.memberIds },
            onDone = { chosen ->
                picking = false
                if (chosen.isNotEmpty()) scope.launch {
                    val added = viewModel.labelStore.addMembers(label, chosen)
                    if (added < chosen.size) {
                        Toast.makeText(context, context.getString(R.string.label_add_skipped, chosen.size - added), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { picking = false },
        )
        return
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(label.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                text = { Text(stringResource(R.string.add_contacts)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            if (members.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.label_empty_title), stringResource(R.string.label_empty_body))
                }
            } else {
                item(key = "count") {
                    Text(
                        pluralStringResource(R.plurals.contact_count, members.size, members.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(members, key = { _, c -> c.id }) { index, contact ->
                Box(Modifier.padding(horizontal = 16.dp).animateItem()) {
                    ContactRow(contact, segmentShape(index, members.size), onClick = { onOpenContact(contact.id) })
                    IconButton(
                        onClick = { scope.launch { viewModel.labelStore.removeMember(label, contact.id) } },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    ) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_label)) }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_label),
            initial = label.title,
            confirmLabel = stringResource(R.string.rename),
            onConfirm = { name -> renaming = false; scope.launch { viewModel.labelStore.rename(label, name) } },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_label_title)) },
            text = { Text(stringResource(R.string.delete_label_body, label.title)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; scope.launch { viewModel.labelStore.delete(label) } }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Choose several contacts: tap rows to tick them, then Add. */
@Composable
private fun ContactPicker(contacts: List<ContactSummary>, onDone: (Set<Long>) -> Unit, onCancel: () -> Unit) {
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                Text(
                    if (chosen.isEmpty()) stringResource(R.string.add_contacts) else pluralStringResource(R.plurals.selected_count, chosen.size, chosen.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = { onDone(chosen) }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.add)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            itemsIndexed(contacts, key = { _, c -> c.id }) { index, contact ->
                val checked = contact.id in chosen
                val toggle = { chosen = if (checked) chosen - contact.id else chosen + contact.id }
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ContactRow(contact, segmentShape(index, contacts.size), onClick = toggle)
                    Checkbox(checked = checked, onCheckedChange = { toggle() }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
