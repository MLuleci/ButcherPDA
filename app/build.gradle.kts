plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Set useFakeErp=true in gradle.properties to run without a server, e.g. on the emulator
            buildConfigField("boolean", "USE_FAKE_ERP", providers.gradleProperty("useFakeErp").getOrElse("false"))
        }
        release {
            buildConfigField("boolean", "USE_FAKE_ERP", "false")
            optimization {
                enable = false
            }
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
