package app.people.contacts.ui.organize

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactAccount
import app.people.contacts.ui.ContactsViewModel
import kotlinx.coroutines.launch

/** Starts exporting every contact to a .vcf file, or importing one. */
class BackupActions(val export: () -> Unit, val import: () -> Unit)

/**
 * Export to and import from .vcf files: the file pickers, and when there's more than one
 * account, which one imported contacts go to.
 */
@Composable
fun rememberBackupActions(viewModel: ContactsViewModel): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val writable = accounts.filter { it.onDevice || it.type != null }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    fun runImport(uri: Uri, account: ContactAccount) {
        scope.launch {
            val added = viewModel.importFrom(uri, account)
            Toast.makeText(context, context.resources.getQuantityString(R.plurals.imported, added, added), Toast.LENGTH_LONG).show()
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) scope.launch {
            val ok = viewModel.exportTo(uri)
            Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (writable.size > 1) importUri = uri else runImport(uri, writable.firstOrNull() ?: ContactAccount(null, null, ""))
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.import_to)) },
            text = {
                Column {
                    writable.forEach { account ->
                        TextButton(onClick = { importUri = null; runImport(uri, account) }, modifier = Modifier.fillMaxWidth()) {
                            Text(account.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { importUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    return remember(exporter, importer) {
        BackupActions(
            export = { exporter.launch("contacts.vcf") },
            import = { importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")) },
        )
    }
}
