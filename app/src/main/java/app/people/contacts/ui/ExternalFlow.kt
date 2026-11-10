package app.people.contacts.ui

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.AccountKeys
import app.people.contacts.data.ContactAccount
import app.people.contacts.data.VCardReader
import app.people.contacts.data.writableAccounts
import app.people.contacts.intents.ExternalRequest
import app.people.contacts.ui.components.ContactRow
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.ui.edit.EditContactScreen
import app.people.contacts.ui.pick.PickScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Requests from other apps that end by handing something back: picking, adding a contact,
 * adding details to one, or editing one. [finish] closes this screen, with a result URI when
 * the request succeeded.
 */
@Composable
fun ExternalFlow(viewModel: ContactsViewModel, request: ExternalRequest, finish: (Uri?) -> Unit) {
    val context = LocalContext.current
    fun resultFor(contactId: Long): Uri? = runCatching {
        ContactsContract.Contacts.getLookupUri(context.contentResolver, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId))
    }.getOrNull()

    when (request) {
        is ExternalRequest.Pick -> PickScreen(viewModel, request.kind, onPicked = { finish(it) }, onCancel = { finish(null) })
        is ExternalRequest.Insert -> EditContactScreen(
            viewModel, id = null, prefill = request.prefill,
            onClose = { finish(null) }, onSaved = { finish(resultFor(it)) },
        )
        is ExternalRequest.Edit -> EditContactScreen(
            viewModel, id = request.contactId,
            onClose = { finish(null) }, onSaved = { finish(resultFor(it)) },
        )
        is ExternalRequest.InsertOrEdit -> {
            // First choose: a new contact, or which existing one gets the details.
            var target by remember { mutableStateOf<Long?>(null) }
            var chosen by remember { mutableStateOf(false) }
            if (!chosen) {
                ChooseContact(viewModel, onNew = { chosen = true }, onPick = { target = it; chosen = true }, onCancel = { finish(null) })
            } else {
                EditContactScreen(
                    viewModel, id = target, prefill = request.prefill,
                    onClose = { if (target == null) chosen = false else { chosen = false; target = null } },
                    onSaved = { finish(resultFor(it)) },
                )
            }
        }
        else -> finish(null)
    }
}

/** "Add to a contact": create a new one, or pick who gets the new details. */
@Composable
private fun ChooseContact(viewModel: ContactsViewModel, onNew: () -> Unit, onPick: (Long) -> Unit, onCancel: () -> Unit) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    BackHandler(onBack = onCancel)
    val list = contacts.orEmpty()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item(key = "top") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.add_to_contact), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
            item(key = "new") {
                ListRow(
                    icon = Icons.Rounded.PersonAdd,
                    title = stringResource(R.string.create_contact),
                    shape = segmentShape(0, 1),
                    onClick = onNew,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
            itemsIndexed(list, key = { _, c -> c.id }) { index, contact ->
                ContactRow(contact, segmentShape(index, list.size), onClick = { onPick(contact.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** A .vcf file opened from another app: how many contacts it holds, and where to add them. */
@Composable
fun ImportFileScreen(viewModel: ContactsViewModel, uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count by produceState<Int?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { VCardReader.parse(String(it.readBytes())).size } }.getOrNull() ?: 0
        }
    }
    val accounts by produceState(emptyList<ContactAccount>()) {
        value = writableAccounts(context, AccountKeys.savedNames(viewModel.settings.savedAccounts.first()))
    }
    var importing by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            val n = count
            when {
                n == null || importing -> LoadingIndicator()
                n == 0 -> EmptyState(
                    Icons.Rounded.Contacts, stringResource(R.string.import_nothing_title), stringResource(R.string.import_nothing_body),
                    action = { Button(onClick = onDone) { Text(stringResource(R.string.ok)) } },
                )
                else -> EmptyState(
                    Icons.Rounded.Contacts,
                    pluralStringResource(R.plurals.import_file_title, n, n),
                    stringResource(R.string.import_file_body),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { account ->
                                Button(onClick = {
                                    importing = true
                                    scope.launch {
                                        val added = viewModel.importFrom(uri, account)
                                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.imported, added, added), Toast.LENGTH_LONG).show()
                                        onDone()
                                    }
                                }) { Text(stringResource(R.string.import_into, account.label)) }
                            }
                            TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
                        }
                    },
                )
            }
        }
    }
}
