# Contacts

A personal fork of the Contacts app from [vayun-mathur/Modern-Apps](https://github.com/vayun-mathur/Modern-Apps),
restyled to follow Material 3 Expressive more closely (Google Contacts–style layout).

- Upstream source revision: see `UPSTREAM_COMMIT`
- App ID: `io.github.jawsh207.contacts`, so it installs alongside the upstream app
- License: GPL-3.0, same as upstream (see `LICENSE`)

## Building

Every push to `main` builds a release APK with GitHub Actions. Download it from the
**contacts-apk** artifact on the run's page under the Actions tab.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :contacts:assembleRelease
```

### Stable signing key (recommended)

Without a key, each CI build is signed with a throwaway debug key, so a new build can't
be installed over an older one without uninstalling first. To fix that, create a keystore
once and add it as repository secrets (Settings → Secrets and variables → Actions):

```sh
keytool -genkeypair -v -keystore release.jks -alias contacts -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # value for KEYSTORE_BASE64
```

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of `release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `contacts` |
| `KEY_PASSWORD` | key password |

Keep `release.jks` and its passwords somewhere safe and out of this repo.
