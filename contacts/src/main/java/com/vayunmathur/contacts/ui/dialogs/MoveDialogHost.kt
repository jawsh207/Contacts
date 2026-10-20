package com.vayunmathur.contacts.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.util.ContactAccount
import com.vayunmathur.contacts.util.ContactViewModel
import com.vayunmathur.contacts.util.transferContacts
import com.vayunmathur.library.ui.rememberMessenger

/**
 * Hosts [MoveToAccountDialog] for [contactIds]: resolves source accounts, runs the transfer, and reports the result via messenger.
 * [onTransferred] fires when at least one contact moved (before [onDismiss]).
 */
@Composable
fun MoveDialogHost(
    viewModel: ContactViewModel,
    contactIds: List<Long>,
    onDismiss: () -> Unit,
    onTransferred: () -> Unit = {},
) {
    val context = LocalContext.current
    val messenger = rememberMessenger()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    val toMove = remember(contacts, contactIds) {
        contacts.filter { it.id in contactIds }
    }
    var selectedAccount by remember(contactIds) { mutableStateOf<ContactAccount?>(null) }

    val sourceAccounts = remember(toMove) {
        toMove.mapTo(mutableSetOf()) {
            ContactAccount(it.accountName.orEmpty(), it.accountType.orEmpty())
        }
    }

    MoveToAccountDialog(
        contactCount = toMove.size.coerceAtLeast(1),
        accounts = accounts,
        sourceAccounts = sourceAccounts,
        selectedAccount = selectedAccount,
        onSelectAccount = { selectedAccount = it },
        onConfirm = {
            val dest = selectedAccount ?: return@MoveToAccountDialog
            val ids = toMove.map { it.id }.ifEmpty { contactIds }
            viewModel.transferContacts(ids, dest.name, dest.type) { moved, failed, _ ->
                // Resources (not composable stringResource): this callback runs
                // outside composition on the main thread.
                val message = when {
                    moved > 0 && failed == 0 ->
                        context.resources.getQuantityString(R.plurals.move_success, moved, moved)
                    moved > 0 ->
                        context.getString(R.string.move_partial_failure, moved, failed)
                    else -> context.getString(R.string.move_failed)
                }
                messenger.show(message)
                if (moved > 0) onTransferred()
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    )
}
