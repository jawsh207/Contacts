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
    defaultConfig {
        applicationId = "io.github.jawsh207.contacts"
    }
}

dependencies {
    // VCF export/import now uses java.io BufferedReader/Writer – no okio needed
    implementation(libs.libphonenumber)
    implementation(libs.androidx.work.runtime.ktx)
    // QR code for "Your info"
    implementation(libs.zxing.core)
}
