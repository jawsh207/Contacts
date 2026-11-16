# How Contacts works

A plain-language guide to the app: what each part does and where it lives in the code. No
programming knowledge needed. File paths are inside `app/src/main/java/app/people/contacts/`.

## The big picture

Contacts doesn't keep its own list of people. Android has a shared contacts store, the
**contacts provider**, that every app (the phone app, messaging, Google or other account sync)
reads and writes. This app is a window onto that store: it shows what's there, and when you
save, delete, merge or label someone, it asks the provider to make the change. That's why a
contact edited here changes everywhere else too, and why contacts from your accounts appear
without any setup.

Each person you see is usually one **contact** built from one or more **saved copies** (Android
calls them raw contacts), one per place the person is stored: the phone itself, a Google
account, a CardDAV account and so on. Android links copies that look like the same person
into one contact. Merging links copies on purpose; unlinking splits them apart again.

The app watches the provider, so when anything changes, even from another app, every list
refreshes by itself.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| Contacts tab | Your info card, Favorites, everyone in A–Z groups, the A–Z strip, selecting several contacts | `ui/contacts/ContactsTab.kt` |
| Highlights tab | Favorites, upcoming birthdays and Recents | `ui/highlights/HighlightsTab.kt`, `HighlightsComponents.kt` |
| Organize tab | Labels, Merge duplicates, Fix contacts, Import and Export | `ui/organize/OrganizeTab.kt` |
| Search | Finding people by name, number, email, company, job title, nickname, notes or address | `ui/search/SearchScreen.kt` |
| Contact page | One person's details, with call, text, email, share, move, unlink and delete | `ui/contact/ContactScreen.kt`, `ContactHeader.kt`, `ContactSections.kt` |
| Editor | Creating or changing a contact or Your info | `ui/edit/EditContactScreen.kt`, `EditFields.kt` |
| Photo cropper | Choosing which part of a picked photo shows in the circle | `ui/edit/PhotoCropper.kt` |
| Label page | Who's in a label, adding and removing people, texting or emailing everyone | `ui/organize/LabelScreen.kt` |
| Merge duplicates | Contacts that look like the same person, to merge or dismiss | `ui/organize/MergeScreen.kt` |
| Fix contacts | Empty contacts, contacts with no name, numbers saved twice, numbers missing a country code | `ui/organize/FixContactsScreen.kt` |
| Settings | Name order, hiding contacts without a number, Recents, the birthday calendar, accounts | `ui/settings/SettingsScreen.kt` |
| Pick a contact | Shown when another app asks you to choose a contact, number or email | `ui/pick/PickScreen.kt` |
| Add to a contact, Import file | Shown when another app sends a number to save or a .vcf file to open | `ui/ExternalFlow.kt` |

The main screen (the three tabs with the search bar and bottom bar) and moving between
screens are in `ui/ContactsApp.kt` and `ui/Navigation.kt`. Shared pieces such as the round
photos, rows and grouped cards are in `ui/components/`, and the colours and fonts in
`ui/theme/Theme.kt`, which matches Dialtone's.

## Features

**The list.** All contacts in accounts you haven't hidden, sorted by first or last name (the
Name order setting). Sorted by last name, names also show as "Last, First". A setting can
hide contacts with no phone number from the list, search and Highlights.

**Selecting several.** Long-press a contact to start. The search bar turns into a bar of
actions: favorite, share (as one .vcf file), delete, select all, add to a label.

**Deleting with Undo.** A deleted contact disappears at once and an Undo bar shows for 8
seconds; only then is it really deleted. If the app closes first, the deletion goes ahead.

**Editing.** The editor saves only what changed, row by row, so details the app doesn't show
(added by other apps) are left alone. Fields: name parts and phonetic names, nickname,
company and title, numbers, emails, addresses, dates, websites, relationships, internet call
and chat addresses, notes, labels and a photo. New contacts go to the account you saved to
last.

**Photos.** A picked photo opens in the cropper: pinch and drag under a round frame. The
chosen square is shrunk to 720×720 and saved as the contact's photo; the provider makes the
small version for lists.

**Default number or email.** For someone with several, long-press one to make it the default.
Call, text and email buttons, and other apps, then use it.

**Merging and unlinking.** Merge duplicates finds contacts sharing a name, a number (last 10
digits, however it's written) or an email, including through each other. Merging links them
into one contact, as Google Contacts does; nothing is deleted. Unlink splits a linked contact
apart and tells Android not to link those copies again.

**Fix contacts.** Looks for contacts with nothing in them, contacts showing a number or email
because they have no name, the same number saved twice on one saved copy, and numbers without
a country code (worked out from the SIM, the network or the phone's region). Each has a fix.

**Labels.** Android's contact groups, per account. A label's page can text or email everyone
in it at once.

**Highlights.** Favorites as large round photos; birthdays in the next 30 days (February 29
shows on February 28 in other years); and Recents: contacts you opened, or, if you allow it in
Settings, your latest calls and texts with saved contacts. Texts you sent show a send arrow
and "You:".

**Birthday calendar.** Optional. The app keeps a "Contact Birthdays" calendar in the phone's
calendar app, with each birthday and the age turned, refreshed daily in the background.

**Your info.** Your own card (Android's profile). Its QR code holds your details as a vCard,
so someone can scan it with their camera to save you.

**Import and export.** Export writes every contact to one .vcf file using Android's own vCard
writer. Import reads .vcf files from other apps (vCard 2.1, 3.0 and 4.0) and saves the
contacts to the account you choose.

**Requests from other apps.** Other apps can ask Contacts to pick a contact, add one, add a
number to an existing one, open one, or open a .vcf file. These come in through
`intents/ExternalRequest.kt`.

## Where the data work happens

| Job | File |
| --- | --- |
| Reading contacts and their details | `data/ContactsRepository.kt` |
| Saving, moving and deleting | `data/ContactWriter.kt` |
| What the editor works on | `data/EditableContact.kt` |
| Search | `data/Search.kt` |
| Duplicates and merging | `data/Duplicates.kt` |
| Fix contacts | `data/Fixes.kt` |
| Labels | `data/Labels.kt` |
| Accounts | `data/Accounts.kt` |
| Birthdays in Highlights | `data/Birthdays.kt` |
| Recents | `data/RecentActivity.kt` |
| .vcf files | `data/VCard.kt` |
| Settings | `data/Settings.kt` |
| The birthday calendar | `calendar/` |
| Photo loading and cropping | `util/PhotoScaler.kt`, `util/PhotoCrop.kt` |
| Opening other apps (call, text, map…) | `util/Intents.kt` |
| Everything the screens show, in one place | `ui/ContactsViewModel.kt` |

## Permissions

| Permission | Why |
| --- | --- |
| Contacts (read and write) | The whole app: showing and changing contacts |
| Calendar (read and write) | The optional birthday calendar |
| Call log and SMS (read) | Only if you turn on calls and texts in Recents |

The app doesn't ask for internet access: nothing you store leaves the phone through it.

## Tests

Automated tests in `app/src/test/` check the logic that's easy to get subtly wrong: reading
.vcf files, reading dates, birthdays, finding duplicates, search, matching numbers, and the
photo cropper's maths. They run on every build; a failing test stops it.
