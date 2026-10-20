package com.vayunmathur.contacts.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.util.ContactAccount
import com.vayunmathur.library.ui.AlertDialog
import com.vayunmathur.library.ui.IconCheck
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Text
import com.vayunmathur.library.ui.TextButton
import com.vayunmathur.library.ui.R as UiR

/**
 * Stateless move-to-account dialog: pick a destination account, then confirm.
 * Same-account destinations are shown disabled.
 */
@Composable
fun MoveToAccountDialog(
    contactCount: Int,
    accounts: List<ContactAccount>,
    sourceAccounts: Set<ContactAccount>,
    selectedAccount: ContactAccount?,
    onSelectAccount: (ContactAccount) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val destination = selectedAccount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_account)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    pluralStringResource(R.plurals.move_to_account_subtitle, contactCount, contactCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(accounts, key = { "${it.type}|${it.name}" }) { account ->
                        DestinationRow(
                            account = account,
                            selected = destination == account,
                            enabled = account !in sourceAccounts,
                            onClick = { onSelectAccount(account) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = destination != null && destination !in sourceAccounts,
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.move))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UiR.string.cancel))
            }
        },
    )
}

@Composable
private fun DestinationRow(
    account: ContactAccount,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val onDevice = stringResource(R.string.on_device)
    val label = account.name.ifEmpty { onDevice }
    val supporting = account.type.takeIf { it.isNotBlank() }
    ListItem(
        content = {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (selected) {
            { IconCheck() }
        } else null,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
