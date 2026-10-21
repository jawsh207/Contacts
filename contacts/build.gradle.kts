plugins {
    id("common-conventions-app")
    // Listing screenshots come from Compose previews (src/screenshotTest), not from an
    // instrumented test on a device. Same `:contacts:metadata` task name either way.
    id("common-conventions-preview-metadata")
}

launcherIcon {
    // Ships its own icon: GrapheneOS Contacts' contact-card glyph in white on Common Teal
    // (#009193); see res/mipmap-anydpi and res/drawable/ic_launcher_foreground.xml.
    custom = true
}

android {
    buildFeatures {
        resValues = true
    }
    defaultConfig {
        applicationId = "io.github.jawsh207.contacts"
        // Account type for the app's local accounts and birthday calendar: always the
        // application id, so the dev build below doesn't share the release build's.
        resValue("string", "account_type", "io.github.jawsh207.contacts")
    }
    buildTypes {
        // Test builds from CI: their own app id, name and signing key, so they install
        // alongside a real release instead of conflicting with it.
        getByName("dev") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "account_type", "io.github.jawsh207.contacts.dev")
        }
    }
}

dependencies {
    // VCF export/import now uses java.io BufferedReader/Writer – no okio needed
    implementation(libs.libphonenumber)
    implementation(libs.androidx.work.runtime.ktx)
    // QR code for "Your info"
    implementation(libs.zxing.core)
}
