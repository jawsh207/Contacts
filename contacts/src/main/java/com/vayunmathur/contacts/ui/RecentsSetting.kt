package com.vayunmathur.contacts.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.RecentActivitySource
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.Switch
import com.vayunmathur.library.ui.Text

/**
 * Switches Highlights' Recents between its two sources: off (the default) shows contacts
 * recently viewed in this app, which needs no permissions; on shows calls and texts from saved
 * contacts instead. Turning it on asks for call log and SMS access and only stays on if both
 * are granted.
 */
@Composable
fun RecentsSetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onEnabledChange(results.isNotEmpty() && results.values.all { it })
    }
    // Shown off if access was revoked in system settings since it was turned on.
    val effective = enabled && RecentActivitySource.hasCallAndMessageAccess(context)
    ListItem(
        supportingContent = { Text(stringResource(R.string.recents_calls_messages_summary)) },
        trailingContent = {
            Switch(
                checked = effective,
                onCheckedChange = { turnOn ->
                    when {
                        !turnOn -> onEnabledChange(false)
                        RecentActivitySource.hasCallAndMessageAccess(context) -> onEnabledChange(true)
                        else -> permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)
                        )
                    }
                },
            )
        },
    ) {
        Text(stringResource(R.string.recents_calls_messages_title))
    }
}
