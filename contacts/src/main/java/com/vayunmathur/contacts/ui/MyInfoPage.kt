package com.vayunmathur.contacts.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.Route
import com.vayunmathur.contacts.data.MyInfo
import com.vayunmathur.contacts.data.encodeMyPhoto
import com.vayunmathur.contacts.util.ContactViewModel
import com.vayunmathur.library.ui.Button
import com.vayunmathur.library.ui.DetailLazyColumn
import com.vayunmathur.library.ui.FilledTonalButton
import com.vayunmathur.library.ui.IconButton
import com.vayunmathur.library.ui.IconEdit
import com.vayunmathur.library.ui.IconShare
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.OutlinedTextField
import com.vayunmathur.library.ui.R as UiR
import com.vayunmathur.library.ui.Text
import com.vayunmathur.library.ui.TextButton
import com.vayunmathur.library.ui.appBarScrollBehavior
import com.vayunmathur.library.util.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QR_SIZE = 240.dp

/**
 * "Your info": the user's own card with a QR code others can scan to save it, plus Share and
 * an edit mode. Opens straight into editing when nothing has been filled in yet.
 */
@Composable
fun MyInfoPage(viewModel: ContactViewModel, backStack: NavBackStack<Route>) {
    val info by viewModel.myInfo.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isEditing = editing ?: (info == null || info!!.isEmpty)
    if (isEditing) {
        MyInfoEditor(
            initial = info ?: MyInfo(),
            onCancel = { if (info == null || info!!.isEmpty) backStack.pop() else editing = false },
            onSave = { viewModel.saveMyInfo(it); editing = false },
        )
    } else {
        MyInfoViewer(info ?: MyInfo(), onBack = { backStack.pop() }, onEdit = { editing = true })
    }
}

@Composable
private fun MyInfoViewer(info: MyInfo, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(UiR.string.share)
    val qrSizePx = with(LocalDensity.current) { QR_SIZE.roundToPx() }
    val vcard = remember(info) { info.toVCard() }
    val qr = remember(vcard, qrSizePx) { qrBitmap(vcard, qrSizePx) }
    DetailLazyColumn(
        title = stringResource(R.string.your_info),
        onNavigateBack = onBack,
        actions = { IconButton(onClick = onEdit) { IconEdit() } },
        scrollBehavior = appBarScrollBehavior(),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MyInfoAvatar(info, 112.dp, MaterialTheme.typography.displayMedium)
                Text(
                    info.name.ifBlank { info.phone },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (info.company.isNotBlank()) {
                    Text(info.company, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            // Black on white whatever the theme: scanners read that most reliably.
            GroupedSection(title = null) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        qr?.let {
                            Image(it.asImageBitmap(), contentDescription = stringResource(R.string.your_info_qr), modifier = Modifier.size(QR_SIZE))
                        }
                    }
                    Text(
                        stringResource(R.string.your_info_qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item {
            val lines = listOfNotNull(info.phone.ifBlank { null }, info.email.ifBlank { null })
            if (lines.isNotEmpty()) {
                GroupedSection(title = null) {
                    lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
        }
        item {
            FilledTonalButton(onClick = { shareMyInfo(context, info, shareTitle) }, modifier = Modifier.fillMaxWidth()) {
                IconShare()
                Text(shareTitle, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun MyInfoEditor(initial: MyInfo, onCancel: () -> Unit, onSave: (MyInfo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var company by rememberSaveable { mutableStateOf(initial.company) }
    var photo by remember { mutableStateOf(initial.photo) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                photo = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                            ?.let { encodeMyPhoto(it) }
                    }.getOrNull()
                } ?: photo
            }
        }
    }
    val draft = MyInfo(name = name, phone = phone, email = email, company = company, photo = photo)
    DetailLazyColumn(
        title = stringResource(R.string.your_info),
        onNavigateBack = onCancel,
        actions = {
            Button(onClick = { onSave(draft) }, enabled = !draft.isEmpty) { Text(stringResource(UiR.string.save)) }
        },
        scrollBehavior = appBarScrollBehavior(),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.clickable {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) { MyInfoAvatar(draft, 112.dp, MaterialTheme.typography.displayMedium) }
                if (photo != null) {
                    TextButton(onClick = { photo = null }) { Text(stringResource(R.string.your_info_remove_photo)) }
                } else {
                    TextButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.your_info_add_photo)) }
                }
            }
        }
        item { InfoField(name, { name = it }, stringResource(UiR.string.name), KeyboardType.Text) }
        item { InfoField(phone, { phone = it }, stringResource(R.string.phone), KeyboardType.Phone) }
        item { InfoField(email, { email = it }, stringResource(UiR.string.email), KeyboardType.Email) }
        item { InfoField(company, { company = it }, stringResource(R.string.company), KeyboardType.Text) }
    }
}

@Composable
private fun InfoField(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}
