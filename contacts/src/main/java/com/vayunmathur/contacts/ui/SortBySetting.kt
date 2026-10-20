package com.vayunmathur.contacts.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.util.ContactSorting.ContactSortMode
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.SegmentedButton
import com.vayunmathur.library.ui.SegmentedButtonDefaults
import com.vayunmathur.library.ui.SingleChoiceSegmentedButtonRow
import com.vayunmathur.library.ui.Text

/**
 * "Sort by" first or last name. Lives in Settings, as in Google Contacts, rather than as a menu
 * in the Contacts tab's top bar.
 */
@Composable
fun SortBySetting(sortMode: ContactSortMode, onSortModeChange: (ContactSortMode) -> Unit) {
    val options = listOf(
        ContactSortMode.FirstName to stringResource(R.string.first_name),
        ContactSortMode.LastName to stringResource(R.string.last_name),
    )
    ListItem(
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = sortMode == mode,
                        onClick = { onSortModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }
        },
    ) {
        Text(stringResource(R.string.sort_by))
    }
}
