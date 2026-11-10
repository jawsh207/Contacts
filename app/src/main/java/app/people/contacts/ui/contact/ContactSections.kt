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
)

/** The contact page's cards: contact info, dates, about and labels, each only if it has anything. */
fun LazyListScope.contactSections(contact: ContactDetails) {
    val info = buildList {
        contact.phones.forEach { p ->
            add(
                Detail(
                    Icons.Outlined.Call, formatNumber(p.value), p.label,
                    onClick = { Reach.dial(it, p.value) },
                    action = Icons.Outlined.ChatBubbleOutline to { ctx -> Reach.message(ctx, p.value) },
                    actionDescription = R.string.action_text,
                )
            )
        }
        contact.emails.forEach { e -> add(Detail(Icons.Outlined.Email, e.value, e.label, onClick = { Reach.email(it, e.value) })) }
        contact.addresses.forEach { a -> add(Detail(Icons.Outlined.Place, a.value, a.label, onClick = { Reach.map(it, a.value) })) }
        contact.websites.forEach { w -> add(Detail(Icons.Outlined.Language, w, onClick = { Reach.web(it, w) })) }
    }
    section("info", R.string.section_contact_info, info)

    val dates = contact.events.map { e ->
        val icon = if (e.type == EventKind.TYPE_BIRTHDAY) Icons.Outlined.Cake else Icons.Outlined.Event
        Detail(icon, formatEventDate(e.value), e.label)
    }
    section("dates", R.string.section_dates, dates)

    val about = buildList {
        contact.nickname?.let { add(Detail(Icons.Outlined.Badge, it, null)) }
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
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceBright)
            .combinedClickable(
                onClick = { detail.onClick?.invoke(context) },
                onLongClick = { copy(context, detail.value) },
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(detail.icon, null, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(detail.value, style = MaterialTheme.typography.bodyLarge)
            if (!detail.label.isNullOrEmpty()) {
                Text(detail.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
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
