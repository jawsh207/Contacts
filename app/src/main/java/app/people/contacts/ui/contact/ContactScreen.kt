package app.people.contacts.ui.contact

import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import app.people.contacts.util.qrCode
import app.people.contacts.util.qrVCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactDetails
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.DetailState
import app.people.contacts.util.Reach

/** A contact's page: header, quick actions, then cards of everything stored for them. */
@Composable
fun ContactScreen(viewModel: ContactsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val flow = remember(id) { viewModel.details(id) }
    LaunchedEffect(id) { viewModel.recordView(id) }
    val state by flow.collectAsStateWithLifecycle(DetailState(false, null))
    // Deleted elsewhere while open: nothing to show.
    LaunchedEffect(state) { if (state.loaded && state.details == null) onBack() }
    val contact = state.details
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val isProfile = ContactsContract.isProfileId(id)

    if (confirmDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, contact.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Once it's gone the page closes itself (see above).
                    scope.launch {
                        if (!viewModel.writer.delete(contact.id, contact.lookupKey)) {
                            android.widget.Toast.makeText(context, R.string.delete_failed, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showQr && contact != null) QrDialog(contact, onDismiss = { showQr = false })

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (contact != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") { ContactHeader(contact) }
                    item(key = "actions") { QuickActions(contact) }
                    contactSections(contact)
                    item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
            TopBar(
                contact = contact,
                isProfile = isProfile,
                onBack = onBack,
                onShowQr = { showQr = true },
                onToggleStar = { contact?.let { viewModel.setStarred(it.id, !it.starred) } },
                onEdit = onEdit,
                onShare = { contact?.let { Reach.share(context, it.lookupKey, it.name, profile = isProfile) } },
                onDelete = { confirmDelete = true },
            )
        }
    }
}

/** Back on the left; edit, the Favorites star and a menu on the right, floating over the backdrop. */
@Composable
private fun TopBar(
    contact: ContactDetails?,
    isProfile: Boolean,
    onBack: () -> Unit,
    onShowQr: () -> Unit,
    onToggleStar: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(CONTACT_TOP_BAR_HEIGHT).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
        Spacer(Modifier.weight(1f))
        if (contact != null) {
            if (isProfile) CircleIconButton(Icons.Rounded.QrCode2, stringResource(R.string.show_qr), onShowQr)
            CircleIconButton(Icons.Outlined.Edit, stringResource(R.string.edit_contact), onEdit)
            // Your info can't be a favorite or be deleted from here.
            if (!isProfile) {
                CircleIconButton(
                    if (contact.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    stringResource(if (contact.starred) R.string.remove_from_favorites else R.string.add_to_favorites),
                    onToggleStar,
                )
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                CircleIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                    if (!isProfile) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** An icon button on a translucent circle of the page colour, readable over any photo. */
@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)) {
        IconButton(onClick = onClick) { Icon(icon, description) }
    }
}

/** Call, Text, Video and Email, using the contact's first number and email address. */
@Composable
private fun QuickActions(contact: ContactDetails) {
    val context = LocalContext.current
    val number = contact.phones.firstOrNull()?.value
    val email = contact.emails.firstOrNull()?.value
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.Call, stringResource(R.string.action_call), number != null) {
            number?.let { Reach.dial(context, it) }
        }
        QuickAction(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.action_text), number != null) {
            number?.let { Reach.message(context, it) }
        }
        QuickAction(Icons.Outlined.Videocam, stringResource(R.string.action_video), number != null) {
            number?.let { Reach.videoCall(context, it) }
        }
        QuickAction(Icons.Outlined.Email, stringResource(R.string.action_email), email != null) {
            email?.let { Reach.email(context, it) }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val content = if (enabled) scheme.onSecondaryContainer else scheme.onSurface.copy(alpha = 0.38f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) scheme.secondaryContainer else scheme.onSurface.copy(alpha = 0.12f),
            contentColor = content,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/** Your info as a QR code: anyone can scan it with their camera to save your contact. */
@Composable
private fun QrDialog(contact: ContactDetails, onDismiss: () -> Unit) {
    val code = remember(contact) { qrCode(qrVCard(contact)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (code != null) {
                    // Black on white whatever the theme, so every camera can read it.
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                        Image(
                            code, contentDescription = stringResource(R.string.show_qr),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.padding(12.dp).size(240.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
