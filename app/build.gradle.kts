import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Release signing comes from environment variables, which CI fills from the
// repository secrets. Without them a release build falls back to the debug key:
// it still installs, but it cannot update an APK signed with the real key.
// The app's version: bump both for every release, then tag vX.Y.Z to publish it.
val appVersionCode = 4
val appVersionName = "1.1.1"

// Where the app looks for updates (its GitHub releases).
val updateApiUrl = "https://api.github.com/repos/ocpabdurrahim/fintrack-android/releases/latest"

val releaseKeystore: File? = System.getenv("FIN_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "io.github.ocpabdurrahim.fintrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ocpabdurrahim.fintrack"
        minSdk = 26
        targetSdk = 35
        // The overrides exist for CI's self-update test, which builds a "newer" copy.
        versionCode = (findProperty("versionCodeOverride") as String?)?.toInt() ?: appVersionCode
        versionName = (findProperty("versionNameOverride") as String?) ?: appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_API_URL", "\"$updateApiUrl\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("FIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FIN_KEY_ALIAS")
                keyPassword = System.getenv("FIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a test build sit next to the real app on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // -PfirebaseEmulatorHost=10.0.2.2 points a test build at the local Firebase
            // emulators (10.0.2.2 is the computer, seen from an Android emulator).
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"${findProperty("firebaseEmulatorHost") ?: ""}\"")
            // -PupdateApiUrl=... lets a test build update itself from a local server.
            buildConfigField("String", "UPDATE_API_URL", "\"${findProperty("updateApiUrl") ?: updateApiUrl}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // Android's org.json is a stub on the JVM; this is the real implementation.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
