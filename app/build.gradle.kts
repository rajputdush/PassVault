import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing details live in keystore.properties (see RELEASE.md). That file and the .jks are never committed.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// Optional: a web address that makes shared items tappable in WhatsApp and other chat apps (they do not turn
// passvault:// links into taps). Host the page in docs/share/ there, then set in local.properties:
//     share.host=digitalutilization.com
//     share.path=/passvault/share        (optional, this is the default)
val shareHost = (localProps.getProperty("share.host") ?: (findProperty("share.host") as String?) ?: "").trim()
val sharePath = (localProps.getProperty("share.path") ?: (findProperty("share.path") as String?) ?: "/passvault/share").trim().trimEnd('/')
val shareWebUrl = if (shareHost.isEmpty()) "" else "https://$shareHost$sharePath/"

android {
    namespace = "com.ritvik.passvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ritvik.passvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SHARE_WEB_URL", "\"$shareWebUrl\"")
        buildConfigField("String", "SHARE_HOST", "\"$shareHost\"")
        manifestPlaceholders["shareHost"] = shareHost.ifEmpty { "share.invalid" }
        manifestPlaceholders["sharePath"] = sharePath.ifEmpty { "/passvault/share" }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Without keystore.properties the release build is simply unsigned (fine for a local check).
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.argon2kt)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    // QR scanner for 2FA setup codes. It runs inside Google Play services, so PassVault needs no camera permission.
    implementation(libs.play.services.code.scanner)
    // Draws the QR code when sharing an item. Pure Java, works offline, no permissions.
    implementation(libs.zxing.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
