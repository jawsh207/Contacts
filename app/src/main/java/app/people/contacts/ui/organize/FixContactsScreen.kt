package app.people.contacts.ui.organize

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.FixItem
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.util.formatNumber

/**
 * Fix contacts: contacts with no name, empty contacts, numbers saved twice on one contact, and
 * numbers missing a country code, each with a way to fix it.
 */
@Composable
fun FixContactsScreen(viewModel: ContactsViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val found by viewModel.fixes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Rows already sent to be fixed, hidden until the contacts reload without them.
    var done by remember { mutableStateOf(emptySet<Long>()) }
    val fixes = found
    val unnamed = fixes?.unnamed.orEmpty()
    val empty = fixes?.empty.orEmpty().filter { it.contactId !in done }
    val repeated = fixes?.repeatedNumbers.orEmpty().filter { it.dataId !in done }
    val countryCodes = fixes?.countryCodes.orEmpty().filter { it.dataId !in done }

    fun toast(changed: Int, asked: Int) {
        val text = if (changed == asked) context.resources.getQuantityString(R.plurals.fixed_count, changed, changed)
        else context.getString(R.string.fix_failed)
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    fun removeRepeated(items: List<FixItem>) {
        done = done + items.map { it.dataId }
        viewModel.removeRepeatedNumbers(items) { toast(it, items.size) }
    }
    fun addCodes(items: List<FixItem>) {
        done = done + items.map { it.dataId }
        viewModel.addCountryCodes(items) { toast(it, items.size) }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.fix_contacts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            when {
                fixes == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                unnamed.isEmpty() && empty.isEmpty() && repeated.isEmpty() && countryCodes.isEmpty() -> item(key = "none") {
                    EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.fix_none_title), stringResource(R.string.fix_none_body))
                }
            }

            if (empty.isNotEmpty()) {
                fixSection("empty", R.string.fix_empty_title, R.string.fix_empty_body, actionLabel = R.string.delete_all, onAction = {
                    done = done + empty.map { it.contactId }
                    viewModel.delete(empty.map { it.contactId to it.lookupKey }, null)
                })
                item(key = "empty-row") {
                    ListRow(
                        icon = Icons.Outlined.DeleteSweep,
                        title = pluralStringResource(R.plurals.fix_empty_count, empty.size, empty.size),
                        shape = segmentShape(0, 1),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (unnamed.isNotEmpty()) {
                fixSection("unnamed", R.string.fix_unnamed_title, R.string.fix_unnamed_body)
                fixRows("unnamed", unnamed, Icons.Outlined.PersonOff, { it.contactId }, summary = { null }, onClick = { onEdit(it.contactId) }) {
                    TextButton(onClick = { onEdit(it.contactId) }) { Text(stringResource(R.string.add_name)) }
                }
            }

            if (repeated.isNotEmpty()) {
                fixSection("repeated", R.string.fix_repeated_title, R.string.fix_repeated_body, R.string.fix_all, onAction = { removeRepeated(repeated) })
                fixRows("repeated", repeated, Icons.Outlined.CopyAll, { it.dataId }, summary = { formatNumber(it.value) }) {
                    TextButton(onClick = { removeRepeated(listOf(it)) }) { Text(stringResource(R.string.remove)) }
                }
            }

            if (countryCodes.isNotEmpty()) {
                fixSection("codes", R.string.fix_codes_title, R.string.fix_codes_body, R.string.fix_all, onAction = { addCodes(countryCodes) })
                fixRows("codes", countryCodes, Icons.Outlined.Language, { it.dataId }, summary = { "${it.value} → ${it.fixed}" }) {
                    TextButton(onClick = { addCodes(listOf(it)) }) { Text(stringResource(R.string.fix)) }
                }
            }
        }
    }
}

/** A kind of fix: its heading, what it means, and optionally a button for all of them. */
private fun LazyListScope.fixSection(key: String, title: Int, body: Int, actionLabel: Int? = null, onAction: () -> Unit = {}) {
    item(key = "$key-header") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
            Box(Modifier.weight(1f)) { SectionHeader(stringResource(title)) }
            if (actionLabel != null) TextButton(onClick = onAction) { Text(stringResource(actionLabel)) }
        }
    }
    item(key = "$key-body") {
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        )
    }
}

private fun LazyListScope.fixRows(
    key: String,
    items: List<FixItem>,
    icon: ImageVector,
    id: (FixItem) -> Long,
    summary: (FixItem) -> String?,
    onClick: ((FixItem) -> Unit)? = null,
    action: @Composable (FixItem) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> "$key-${id(item)}" }) { index, item ->
        ListRow(
            icon = icon,
            title = item.name,
            summary = summary(item),
            shape = segmentShape(index, items.size),
            onClick = onClick?.let { { it(item) } },
            trailing = { action(item) },
            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
        )
    }
}
