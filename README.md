# Contacts

A personal fork of the Contacts app from [vayun-mathur/Modern-Apps](https://github.com/vayun-mathur/Modern-Apps),
restyled to follow Material 3 Expressive more closely (Google Contacts–style layout).

- Upstream source revision: see `UPSTREAM_COMMIT`
- App ID: `io.github.jawsh207.contacts`, so it installs alongside the upstream app
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
| **Test build** | every push to `main`, PRs, manual runs | "Contacts Dev" (`io.github.jawsh207.contacts.dev`) | `contacts-dev-apk` on the run page |
| **Release** | pushing a version tag, e.g. `git tag v1.0 && git push origin v1.0` | "Contacts" (`io.github.jawsh207.contacts`) | attached to a GitHub Release |

Because they use different app ids, a test build and a release install side by side. Each run's
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
