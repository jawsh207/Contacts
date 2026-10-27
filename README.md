# Contacts

A personal fork of the Contacts app from [vayun-mathur/Modern-Apps](https://github.com/vayun-mathur/Modern-Apps),
restyled to follow Material 3 Expressive more closely (Google Contacts–style layout).

- Upstream source revision: see `UPSTREAM_COMMIT`
- App ID: `app.people.contacts`, so it installs alongside the upstream app
- License: GPL-3.0, same as upstream (see `LICENSE`)
- Bundles the Google Sans Flex typeface, unmodified, from [google/fonts](https://github.com/google/fonts/tree/main/ofl/googlesansflex)
  under the SIL Open Font License 1.1 (`library/ui/src/main/assets/licenses/google_sans_flex_OFL.txt`).
  Google Sans Flex is a trademark of Google LLC; this project is not affiliated with Google.
- Launcher icon: the contact-card glyph from [GrapheneOS Contacts](https://github.com/GrapheneOS/platform_packages_apps_Contacts)
  (Apache 2.0), recolored white on Common Teal (`#009193`).

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Contacts Dev" (`app.people.contacts.dev`) | `contacts-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch (`git push origin main:release`), or a version tag such as `v1.0` | "Contacts" (`app.people.contacts`) | attached to a GitHub Release |

The **Build release** workflow does a whole release in one go: it raises the version (patch by
default; minor or major if you pick it), adds a `CHANGELOG.md` section listing the commits since
the last release (with an optional summary above them) unless one is already written, commits
both to `main` and `release`, then builds, signs and publishes the release.

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Because they use different app ids, a test build and a release install side by
side. Each run's
page shows the APK's signing certificate SHA-256, so you can check it never changes.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :contacts:assembleDev       # test build
./gradlew :contacts:assembleRelease   # release
```

### Signing keys

Set as repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DEV_KEYSTORE_BASE64` | base64 of the dev keystore (alias `contacts-dev`) |
| `DEV_KEYSTORE_PASSWORD` | its password (store and key) |
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore (alias `contacts`) |
| `RELEASE_KEYSTORE_PASSWORD` | its password (store and key) |

A test build without the dev key falls back to a throwaway key (and says so); a release without
the release key fails rather than publish an unsigned APK. Keep both keystores and passwords
somewhere safe and out of this repo: a lost key means the installed app can't be updated.
