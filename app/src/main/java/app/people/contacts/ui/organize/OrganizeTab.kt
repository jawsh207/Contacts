package app.people.contacts.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactAccount
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.TextInputDialog
import app.people.contacts.ui.components.segmentShape
import kotlinx.coroutines.launch

/** The Organize tab: labels, then Merge duplicates and Settings. */
@Composable
fun OrganizeTab(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenLabel: (Long) -> Unit,
    onOpenMerge: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf<ContactAccount?>(null) }
    var chooseAccount by remember { mutableStateOf(false) }
    val list = labels.orEmpty()
    // Labels can go in any account that accepts changes; with only the phone there's no choice.
    val labelAccounts = accounts.filter { it.onDevice || !it.type.isNullOrEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        item(key = "labels-header") { SectionHeader(stringResource(R.string.section_labels)) }
        val rows = list.size + 1
        itemsIndexed(list, key = { _, l -> "label-${l.id}" }) { index, label ->
            ListRow(
                icon = Icons.AutoMirrored.Outlined.Label,
                title = label.title,
                summary = listOfNotNull(
                    pluralStringResource(R.plurals.contact_count, label.memberIds.size, label.memberIds.size),
                    label.accountName.takeIf { labelAccounts.size > 1 },
                ).joinToString(" · "),
                shape = segmentShape(index, rows),
                onClick = { onOpenLabel(label.id) },
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
        item(key = "label-create") {
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp)) {
                ListRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.create_label),
                    shape = segmentShape(rows - 1, rows),
                    onClick = {
                        if (labelAccounts.size > 1) chooseAccount = true
                        else creating = labelAccounts.firstOrNull() ?: ContactAccount(null, null, "")
                    },
                )
                DropdownMenu(expanded = chooseAccount, onDismissRequest = { chooseAccount = false }) {
                    Text(
                        stringResource(R.string.create_label_in),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    labelAccounts.forEach { account ->
                        DropdownMenuItem(text = { Text(account.label) }, onClick = { chooseAccount = false; creating = account })
                    }
                }
            }
        }

        item(key = "manage-header") { SectionHeader(stringResource(R.string.fix_and_manage)) }
        item(key = "merge") {
            ListRow(
                icon = Icons.Outlined.AutoFixHigh,
                title = stringResource(R.string.merge_duplicates),
                summary = if (duplicates.isEmpty()) stringResource(R.string.merge_none)
                else pluralStringResource(R.plurals.merge_suggestions, duplicates.size, duplicates.size),
                summaryColor = if (duplicates.isEmpty()) null else MaterialTheme.colorScheme.primary,
                shape = segmentShape(0, 2),
                onClick = onOpenMerge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "settings") {
            ListRow(
                icon = Icons.Outlined.Settings,
                title = stringResource(R.string.settings),
                summary = stringResource(R.string.settings_summary),
                shape = segmentShape(1, 2),
                onClick = onOpenSettings,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    creating?.let { account ->
        TextInputDialog(
            title = stringResource(R.string.create_label),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.label_name),
            onConfirm = { name ->
                creating = null
                scope.launch { viewModel.labelStore.create(name, account) }
            },
            onDismiss = { creating = null },
        )
    }
}
