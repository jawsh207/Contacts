package com.vayunmathur.contacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.ContactDetails
import com.vayunmathur.contacts.data.ContactGroup
import com.vayunmathur.contacts.util.ContactPlatforms
import com.vayunmathur.library.ui.IconLabel
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Surface
import com.vayunmathur.library.ui.Text
import com.vayunmathur.library.util.sharedContainer

/** True when the contact has anything for the "Contact info" card to show. */
internal fun ContactDetails.hasContactInfo(): Boolean =
    phoneNumbers.isNotEmpty() || emails.isNotEmpty() || addresses.isNotEmpty()

/**
 * Google Contacts' "Contact info" card: a titled card holding every phone number, email and
 * address, drawn as one segmented group rather than a separate card per kind.
 */
@Composable
internal fun ContactInfoSection(
    details: ContactDetails,
    platforms: ContactPlatforms,
    arriving: Boolean,
) {
    val phones = details.phoneNumbers.size
    val emails = details.emails.size
    // The title is the group's first segment, so it shares the card's rounded top.
    val total = 1 + phones + emails + details.addresses.size
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = groupShape(0, total),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.contact_info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        if (phones > 0) PhonesSection(details, platforms, arriving, startIndex = 1, totalCount = total)
        if (emails > 0) EmailsSection(details, arriving, startIndex = 1 + phones, totalCount = total)
        if (details.addresses.isNotEmpty()) {
            AddressesSection(details, startIndex = 1 + phones + emails, totalCount = total)
        }
    }
}

/** Google Contacts' "Labels" card: every label (group) the contact belongs to. */
@Composable
internal fun LabelsSection(contact: Contact, groups: List<ContactGroup>) {
    val contactGroups = contactGroupsOf(contact, groups)
    if (contactGroups.isEmpty()) return
    GroupedSection(title = stringResource(R.string.groups)) {
        contactGroups.forEach { group ->
            ListItem(
                modifier = Modifier.sharedContainer("contact-group-${contact.id}-${group.id}"),
                leadingContent = { IconLabel() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) {
                Text(group.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
