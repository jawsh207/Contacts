# Contributing

See README.md for the build setup.

## Commits

Commit as the repo owner, `jawsh207 <76641448+jawsh207@users.noreply.github.com>`, with no
co-author trailers or session links. Don't credit any tools or services in files, commit
messages, release notes or pull requests.

## Code

- Kotlin and Jetpack Compose, Material 3 Expressive (material3 1.5).
- Original code only. Don't copy code from the old Contacts app or its upstream
  (vayun-mathur/Modern-Apps, GPL-3.0); use it only to see how a feature should behave. Code
  from Dialtone (also Apache 2.0, same owner) can be reused freely.
- Keep the look in step with Dialtone: theme, bottom bar, favorite avatars and grouped cards
  should match it exactly.

## Releases

Don't publish a release until this app has every feature of the old Contacts app; until then,
test builds only. When it's ready, the first release replaces the old app (same app id and keys).

Things that must stay compatible with the old app for the update to keep the user's data:

- The settings file (`datastore_default.preferences_pb`) and its keys: see `data/Settings.kt`.
- On-device accounts are stored as contacts with account type `com.vayunmathur.contacts.local`.
- The birthday calendar's account: name "Contacts", type = the app id (`@string/account_type`),
  with the account authenticator declared in the manifest.
