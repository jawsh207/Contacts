package app.people.contacts.ui.edit

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.provider.ContactsContract.CommonDataKinds.Email as EmailKind
import android.provider.ContactsContract.CommonDataKinds.Event as EventKind
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.EditableContact
import app.people.contacts.data.PhotoChange
import app.people.contacts.data.writableAccounts
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.Avatar
import app.people.contacts.util.scaledContactPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The contact editor. [id] null creates a contact; [onSaved] gets the saved contact's id. */
@Composable
fun EditContactScreen(viewModel: ContactsViewModel, id: Long?, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts = remember { writableAccounts(context) }
    var original by remember { mutableStateOf<EditableContact?>(null) }
    var edited by remember { mutableStateOf<EditableContact?>(null) }
    var photo by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == null) {
            edited = EditableContact(account = accounts.first())
        } else {
            val loaded = viewModel.writer.load(id)
            if (loaded == null) onClose() else { original = loaded; edited = loaded }
        }
    }
    val contact = edited
    val dirty = contact != null && (contact != (original ?: EditableContact(account = accounts.first())) || photo != PhotoChange.Keep)
    val close = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = close)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { scaledContactPhoto(context, uri) }?.let { photo = PhotoChange.Set(it) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                Text(
                    stringResource(if (id == null) R.string.create_contact else R.string.edit_contact),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Button(
                    enabled = contact?.hasContent == true && !saving,
                    onClick = {
                        val toSave = contact ?: return@Button
                        saving = true
                        scope.launch {
                            val savedId = viewModel.writer.save(original, toSave, photo)
                            saving = false
                            if (savedId != null) onSaved(savedId) else android.widget.Toast
                                .makeText(context, R.string.save_failed, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.save)) }
            }
            if (contact == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
                ) {
                    PhotoEditor(contact, photo, onPick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, onRemove = { photo = PhotoChange.Remove })
                    if (contact.contactId == null && accounts.size > 1) {
                        AccountPicker(accounts, contact.account) { edited = contact.copy(account = it) }
                    }
                    Fields(contact, context.resources) { edited = it }
                    Spacer(Modifier.navigationBarsPadding().height(24.dp))
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

@Composable
private fun PhotoEditor(contact: EditableContact, photo: PhotoChange, onPick: () -> Unit, onRemove: () -> Unit) {
    val newPhoto = (photo as? PhotoChange.Set)?.jpeg
    val bitmap = remember(newPhoto) { newPhoto?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val hasPhoto = bitmap != null || (photo == PhotoChange.Keep && contact.photoUri != null)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(CircleShape).clickable(onClick = onPick)) {
            if (bitmap != null) {
                Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                val name = listOf(contact.first, contact.last).joinToString(" ").trim()
                Avatar(name, if (photo == PhotoChange.Keep) contact.photoUri else null, contact.contactId ?: 0, 120.dp)
            }
        }
        Row {
            TextButton(onClick = onPick) {
                Text(stringResource(if (hasPhoto) R.string.change_photo else R.string.add_photo))
            }
            if (hasPhoto) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_photo)) }
        }
    }
}

@Composable
private fun AccountPicker(accounts: List<app.people.contacts.data.ContactAccount>, selected: app.people.contacts.data.ContactAccount?, onPick: (app.people.contacts.data.ContactAccount) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.save_to)) },
            trailingIcon = {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.ExpandMore, stringResource(R.string.save_to)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { account ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(account.label) },
                    onClick = { open = false; onPick(account) },
                )
            }
        }
    }
}

/** Every editable field, in Google Contacts' order. */
@Composable
private fun Fields(contact: EditableContact, res: Resources, onChange: (EditableContact) -> Unit) {
    var moreNames by remember {
        mutableStateOf(listOf(contact.prefix, contact.middle, contact.suffix, contact.nickname).any { it.isNotBlank() })
    }
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    Column(verticalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
        if (moreNames) TextInput(R.string.name_prefix, contact.prefix, null, words) { onChange(contact.copy(prefix = it)) }
        TextInput(R.string.first_name, contact.first, Icons.Outlined.Person, words.copy(keyboardType = KeyboardType.Text)) {
            onChange(contact.copy(first = it))
        }
        if (moreNames) TextInput(R.string.middle_name, contact.middle, null, words) { onChange(contact.copy(middle = it)) }
        TextInput(R.string.last_name, contact.last, null, words) { onChange(contact.copy(last = it)) }
        if (moreNames) {
            TextInput(R.string.name_suffix, contact.suffix, null, words) { onChange(contact.copy(suffix = it)) }
            TextInput(R.string.nickname, contact.nickname, null, words) { onChange(contact.copy(nickname = it)) }
        }
        TextButton(onClick = { moreNames = !moreNames }, modifier = Modifier.padding(start = 40.dp)) {
            Text(stringResource(if (moreNames) R.string.fewer_name_fields else R.string.more_name_fields))
            Icon(if (moreNames) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        TextInput(R.string.company, contact.company, Icons.Outlined.Business, words) { onChange(contact.copy(company = it)) }
        TextInput(R.string.job_title, contact.title, null, words) { onChange(contact.copy(title = it)) }
        Spacer(Modifier.height(4.dp))
        RepeatingFields(phoneKind(res), contact.phones) { onChange(contact.copy(phones = it)) }
        RepeatingFields(emailKind(res), contact.emails) { onChange(contact.copy(emails = it)) }
        RepeatingFields(addressKind(res), contact.addresses) { onChange(contact.copy(addresses = it)) }
        RepeatingFields(eventKind(res), contact.events) { onChange(contact.copy(events = it)) }
        RepeatingFields(websiteKind(), contact.websites) { onChange(contact.copy(websites = it)) }
        TextInput(R.string.notes, contact.note, Icons.AutoMirrored.Outlined.Notes, KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), multiLine = true) {
            onChange(contact.copy(note = it))
        }
    }
}

/** A single text field. */
@Composable
private fun TextInput(
    hint: Int,
    value: String,
    icon: ImageVector?,
    keyboard: KeyboardOptions,
    multiLine: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(hint)) },
        leadingIcon = icon?.let { { Icon(it, null) } },
        singleLine = !multiLine,
        minLines = if (multiLine) 3 else 1,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun phoneKind(res: Resources) = FieldKind(
    Icons.Outlined.Call, R.string.phone, R.string.add_phone,
    listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME, Phone.TYPE_PAGER, Phone.TYPE_OTHER),
    Phone.TYPE_CUSTOM, { type, custom -> Phone.getTypeLabel(res, type, custom).toString() },
    keyboard = KeyboardType.Phone,
)

private fun emailKind(res: Resources) = FieldKind(
    Icons.Outlined.Email, R.string.email, R.string.add_email,
    listOf(EmailKind.TYPE_HOME, EmailKind.TYPE_WORK, EmailKind.TYPE_MOBILE, EmailKind.TYPE_OTHER),
    EmailKind.TYPE_CUSTOM, { type, custom -> EmailKind.getTypeLabel(res, type, custom).toString() },
    keyboard = KeyboardType.Email,
)

private fun addressKind(res: Resources) = FieldKind(
    Icons.Outlined.Place, R.string.address, R.string.add_address,
    listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER),
    StructuredPostal.TYPE_CUSTOM, { type, custom -> StructuredPostal.getTypeLabel(res, type, custom).toString() },
    multiLine = true,
)

private fun eventKind(res: Resources) = FieldKind(
    Icons.Outlined.Event, R.string.date, R.string.add_date,
    listOf(EventKind.TYPE_BIRTHDAY, EventKind.TYPE_ANNIVERSARY, EventKind.TYPE_OTHER),
    EventKind.TYPE_CUSTOM, { type, custom -> EventKind.getTypeLabel(res, type, custom).toString() },
    isDate = true,
)

private fun websiteKind() = FieldKind(
    Icons.Outlined.Language, R.string.website, R.string.add_website,
    types = emptyList(), customType = Website.TYPE_OTHER, label = { _, custom -> custom.orEmpty() },
    keyboard = KeyboardType.Uri,
)
