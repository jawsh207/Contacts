package app.people.contacts.ui.pick

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactSummary
import app.people.contacts.intents.PickKind
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ContactRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.util.foldForSearch
import app.people.contacts.util.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One pickable item: a contact, or one of its numbers, emails or addresses. */
private data class PickItem(val key: String, val contact: ContactSummary, val detail: String?, val result: Uri)

/**
 * Lets another app pick a contact, or a contact's phone number, email or address. [onPicked]
 * gets the URI to send back; [onCancel] closes without one.
 */
@Composable
fun PickScreen(viewModel: ContactsViewModel, kind: PickKind, onPicked: (Uri) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val items by produceState(emptyList<PickItem>(), contacts, kind) {
        val list = contacts ?: return@produceState
        value = if (kind == PickKind.Contact) {
            list.map { PickItem("c${it.id}", it, null, ContactsContract.Contacts.getLookupUri(it.id, it.lookupKey)) }
        } else withContext(Dispatchers.IO) { dataItems(context, kind, list) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, query) {
        val q = foldForSearch(query.trim())
        if (q.isEmpty()) items else items.filter { foldForSearch(it.contact.name).contains(q) || it.detail?.let(::foldForSearch)?.contains(q) == true }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item(key = "top") {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth().height(56.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCancel, modifier = Modifier.padding(start = 4.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(pickTitle(kind))) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            itemsIndexed(shown, key = { _, item -> item.key }) { index, item ->
                ContactRow(
                    item.contact, segmentShape(index, shown.size),
                    onClick = { onPicked(item.result) },
                    supporting = item.detail,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

private fun pickTitle(kind: PickKind) = when (kind) {
    PickKind.Contact -> R.string.pick_contact
    PickKind.Phone -> R.string.pick_phone
    PickKind.Email -> R.string.pick_email
    PickKind.Address -> R.string.pick_address
}

/** Every number, email or address of the shown contacts, each with the data row URI to return. */
private fun dataItems(context: Context, kind: PickKind, contacts: List<ContactSummary>): List<PickItem> {
    val byId = contacts.associateBy { it.id }
    val (uri, typeLabel) = when (kind) {
        PickKind.Phone -> Phone.CONTENT_URI to { t: Int, l: String? -> Phone.getTypeLabel(context.resources, t, l) }
        PickKind.Email -> Email.CONTENT_URI to { t: Int, l: String? -> Email.getTypeLabel(context.resources, t, l) }
        else -> StructuredPostal.CONTENT_URI to { t: Int, l: String? -> StructuredPostal.getTypeLabel(context.resources, t, l) }
    }
    val data = ContactsContract.Data.DATA1
    return context.contentResolver.query(
        uri, arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID, data, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
        null, null, null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val contact = byId[c.getLong(1)] ?: continue
                val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val shown = if (kind == PickKind.Phone) formatNumber(value) else value.replace('\n', ' ')
                val label = typeLabel(if (c.isNull(3)) 0 else c.getInt(3), c.getString(4))
                add(PickItem("d${c.getLong(0)}", contact, "$shown · $label", ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, c.getLong(0))))
            }
        }
    }.orEmpty().let { items ->
        val order = contacts.withIndex().associate { it.value.id to it.index }
        items.sortedBy { order[it.contact.id] }
    }
}
