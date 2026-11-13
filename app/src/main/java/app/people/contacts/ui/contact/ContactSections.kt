package app.people.contacts.ui.contact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DialerSip
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import app.people.contacts.data.LabeledValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.provider.ContactsContract.CommonDataKinds.Event as EventKind
import app.people.contacts.R
import app.people.contacts.data.ContactDetails
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.util.Reach
import app.people.contacts.util.formatEventDate
import app.people.contacts.util.formatNumber

/** One row of a section: what it shows, and what tapping it (or its end button) does. */
private data class Detail(
    val icon: ImageVector,
    val value: String,
    val label: String? = null,
    val onClick: ((Context) -> Unit)? = null,
    val action: Pair<ImageVector, (Context) -> Unit>? = null,
    val actionDescription: Int? = null,
    /** For a number or email when there's more than one: make it the default, or stop. */
    val isDefault: Boolean = false,
    val onToggleDefault: (() -> Unit)? = null,
    /** A label from resources, when [label] is null. */
    val labelRes: Int? = null,
)

/**
 * The contact page's cards: contact info, dates, about and labels, each only if it has anything.
 * Long-pressing a number or email (when there's more than one) offers to make it the default,
 * through [onSetDefault] (mime type, row id, on).
 */
fun LazyListScope.contactSections(contact: ContactDetails, onSetDefault: (String, Long, Boolean) -> Unit) {
    fun toggle(mimeType: String, many: Boolean, value: LabeledValue): (() -> Unit)? =
        if (many && value.dataId != 0L) ({ onSetDefault(mimeType, value.dataId, !value.isDefault) }) else null
    val info = buildList {
        contact.phones.forEach { p ->
            add(
                Detail(
                    Icons.Outlined.Call, formatNumber(p.value), p.label,
                    onClick = { Reach.dial(it, p.value) },
                    action = Icons.Outlined.ChatBubbleOutline to { ctx -> Reach.message(ctx, p.value) },
                    actionDescription = R.string.action_text,
                    isDefault = p.isDefault && contact.phones.size > 1,
                    onToggleDefault = toggle(Phone.CONTENT_ITEM_TYPE, contact.phones.size > 1, p),
                )
            )
        }
        contact.emails.forEach { e ->
            add(
                Detail(
                    Icons.Outlined.Email, e.value, e.label, onClick = { Reach.email(it, e.value) },
                    isDefault = e.isDefault && contact.emails.size > 1,
                    onToggleDefault = toggle(Email.CONTENT_ITEM_TYPE, contact.emails.size > 1, e),
                )
            )
        }
        contact.addresses.forEach { a -> add(Detail(Icons.Outlined.Place, a.value, a.label, onClick = { Reach.map(it, a.value) })) }
        contact.websites.forEach { w -> add(Detail(Icons.Outlined.Language, w, onClick = { Reach.web(it, w) })) }
        contact.sipAddresses.forEach { s -> add(Detail(Icons.Outlined.DialerSip, s.value, s.label)) }
        contact.chats.forEach { c -> add(Detail(Icons.Outlined.Forum, c.value, c.label)) }
    }
    section("info", R.string.section_contact_info, info)

    val dates = contact.events.map { e ->
        val icon = if (e.type == EventKind.TYPE_BIRTHDAY) Icons.Outlined.Cake else Icons.Outlined.Event
        Detail(icon, formatEventDate(e.value), e.label)
    }
    section("dates", R.string.section_dates, dates)

    val about = buildList {
        contact.nickname?.let { add(Detail(Icons.Outlined.Badge, it, null)) }
        contact.phoneticName?.let { add(Detail(Icons.Outlined.RecordVoiceOver, it, null, labelRes = R.string.phonetic_name)) }
        contact.relations.forEach { r -> add(Detail(Icons.Outlined.People, r.value, r.label)) }
        contact.notes.forEach { n -> add(Detail(Icons.AutoMirrored.Outlined.Notes, n)) }
    }
    section("about", R.string.section_about, about)

    if (contact.labels.isNotEmpty()) {
        item(key = "labels-header") { SectionHeader(stringResource(R.string.section_labels)) }
        item(key = "labels") { LabelChips(contact.labels) }
    }
}

private fun LazyListScope.section(key: String, title: Int, rows: List<Detail>) {
    if (rows.isEmpty()) return
    item(key = "$key-header") { SectionHeader(stringResource(title)) }
    itemsIndexed(rows, key = { index, _ -> "$key-$index" }) { index, row ->
        DetailRow(row, segmentShape(index, rows.size), Modifier.padding(horizontal = 16.dp, vertical = SEGMENT_GAP / 2))
    }
}

@Composable
private fun DetailRow(detail: Detail, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceBright)
            .combinedClickable(
                onClick = { detail.onClick?.invoke(context) },
                onLongClick = { if (detail.onToggleDefault != null) menu = true else copy(context, detail.value) },
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(detail.icon, null, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(detail.value, style = MaterialTheme.typography.bodyLarge)
            val label = listOfNotNull(
                detail.label?.takeIf { it.isNotEmpty() } ?: detail.labelRes?.let { stringResource(it) },
                if (detail.isDefault) stringResource(R.string.default_label) else null,
            ).joinToString(" · ")
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; copy(context, detail.value) },
                )
                detail.onToggleDefault?.let { toggle ->
                    DropdownMenuItem(
                        text = { Text(stringResource(if (detail.isDefault) R.string.clear_default else R.string.set_default)) },
                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                        onClick = { menu = false; toggle() },
                    )
                }
            }
        }
        detail.action?.let { (icon, run) ->
            IconButton(onClick = { run(context) }) {
                Icon(icon, detail.actionDescription?.let { stringResource(it) }, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LabelChips(labels: List<String>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Sell, null, Modifier.padding(end = 8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Long-press copies a value. Android 13+ shows its own confirmation; older versions get a toast. */
private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
