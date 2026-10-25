import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/** Git's output, or null without git or a repository. */
fun git(vararg args: String): String? = runCatching {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    result.standardOutput.asText.get().trim().takeIf { result.result.get().exitValue == 0 && it.isNotEmpty() }
}.getOrNull()

// The release key stays out of the repo: keystore.properties (git-ignored) in the project root names it.
// Without the file, release builds are unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "bg.butcher.pda"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "bg.butcher.pda"
        minSdk = 23 // Target PDAs are stuck on Android 6.0
        targetSdk = 36
        // The commit count, so each commit on master is newer than the last, which self-update relies on.
        // A shallow clone undercounts, so build releases from a full clone.
        versionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        // The commit, as shown in Settings and used as the release tag
        versionName = git("rev-parse", "--short", "HEAD") ?: "dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // Android 6 only checks the v1 (JAR) signature; v2 is for 7.0 and later
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Set useFakeErp=true in gradle.properties to run without a server, e.g. on the emulator
            buildConfigField("boolean", "USE_FAKE_ERP", providers.gradleProperty("useFakeErp").getOrElse("false"))
        }
        release {
            buildConfigField("boolean", "USE_FAKE_ERP", "false")
            signingConfig = signingConfigs.findByName("release")
            // R8 and resource shrinking; keep rules are in src/main/keepRules. The newer optimization { } block
            // needs an experimental flag before AGP 9.3, so this is the stable form
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        // android.util.Log and friends return defaults instead of throwing in unit tests
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // kotlinx-datetime is backed by java.time, which only exists on API 26+
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.kotlinx.datetime)

    // Vendor scanner/printer SDK; bundles zxing 3.5.1, so don't add zxing separately
    implementation(files("libs/qspdasdk.jar"))

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.tls)
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
