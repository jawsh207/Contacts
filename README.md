# Contacts

A contacts app for Android, built with Jetpack Compose and Material 3 Expressive, styled after
Google Contacts: wallpaper colours, Google Sans Flex, grouped cards and expressive shapes. It
shares its look with the [Dialtone](https://github.com/jawsh207/dialtone) phone app.

Written from scratch, it replaced the earlier Contacts app (a fork of
vayun-mathur/Modern-Apps) from version 2.0.0, with the same app id (`app.people.contacts`) and
signing keys, so it installed over that app as an update.

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Contacts Dev" (`app.people.contacts.dev`) | `contacts-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Contacts" (`app.people.contacts`) | attached to a GitHub Release |

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Each run's page shows the APK's signing certificate SHA-256.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :app:assembleDev       # test build
./gradlew :app:assembleRelease   # release
```

### Signing keys

Set as repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DEV_KEYSTORE_BASE64` | base64 of the dev keystore (alias `contacts-dev`) |
| `DEV_KEYSTORE_PASSWORD` | its password (store and key) |
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore (alias `contacts`) |
| `RELEASE_KEYSTORE_PASSWORD` | its password (store and key) |

## License

Apache License 2.0 (see `LICENSE`). Third-party parts are listed in `NOTICE`.
