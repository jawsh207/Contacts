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

When the owner says "Build release":

1. Collect the changes since the last release: `git log --format=%s origin/release..main`.
2. In `version.txt`, raise line 1 (version code: today's date as YYYYMMDD, or +1 if that is not
   higher) and line 2 (version name, MAJOR.MINOR.PATCH: every release bumps the patch number;
   minor or major only when asked, resetting the parts after it to 0).
3. Add a `## <version name>` section at the top of the sections in `CHANGELOG.md` (one line per
   bullet or paragraph, no hard wraps), in plain, user-facing words, grouped by area.
4. Commit, push `main`, then `git push origin main:release`. CI builds, signs, tags
   `v<version>` and publishes the release with that section as its notes.
5. Check the run: the release signing certificate SHA-256 must be
   `09d3184e2e50bd0f1fff3d66cebfc6935c57301da868d69cef0d59586c8a1f5a`.

The **Build release** workflow in the Actions tab does the same, writing the notes from commit
titles.

Things that must stay compatible with the app 2.0.0 replaced, so updates keep the user's data:

- The settings file (`datastore_default.preferences_pb`) and its keys: see `data/Settings.kt`.
- On-device accounts are stored as contacts with account type `com.vayunmathur.contacts.local`.
- The birthday calendar's account: name "Contacts", type = the app id (`@string/account_type`),
  with the account authenticator declared in the manifest.
