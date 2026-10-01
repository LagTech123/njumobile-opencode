import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pl.nju.opencode"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.nju.opencode"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        // BuildConfig carries the DEMO switch that swaps the live scrape for
        // the fixed snapshot in ui/DemoAccount.kt.
        buildConfig = true
    }

    // Two editions of the same code:
    //   prod  - talks to njumobile.pl with real credentials
    //   demo  - never opens a network socket; boots with DemoAccount so the
    //           dashboard can be photographed without any real account data
    // The appId suffix keeps them side by side on one phone.
    flavorDimensions += "edition"
    productFlavors {
        create("prod") {
            dimension = "edition"
            isDefault = true
            buildConfigField("boolean", "DEMO", "false")
        }
        create("demo") {
            dimension = "edition"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }

    // Release signing is deliberately optional: a fresh clone of this repo has
    // no keystore and must still be able to build (it just yields an unsigned
    // APK). The key is looked for OUTSIDE the repository so it can never be
    // committed, zipped up or uploaded by accident.
    val signingFile = sequenceOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".config/nju-mobile/signing.properties"),
    ).firstOrNull { it.isFile }
    val signingProps = signingFile?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null when no keystore was found -> unsigned release build.
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
