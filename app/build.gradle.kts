plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The Google Services plugin generates FirebaseApp default options from a
// google-services.json placed at this module's root. It is applied ONLY when
// that file exists, so local development without Firebase project config keeps
// building. To enable Firebase App Check, add the file from the Firebase Console
// (steps in functions/README.md). Without it, Firebase App Check is skipped and
// the AI proxy degrades to the offline workflow.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.kemalcetin.aialarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prompthavenai.alarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Non-secret, build-time configurable backend base URL. The local
            // Firebase Functions emulator serves at
            //   http://10.0.2.2:5001/<PROJECT_ID>/us-central1
            // from the Android emulator's perspective (10.0.2.2 == host localhost).
            // Set the Gradle property PROMPTHAVEN_FIREBASE_PROJECT_ID (recommended),
            // or override the whole URL with PROMPTHAVEN_FUNCTIONS_DEBUG_URL.
            // A blank/unconfigured value leaves AI "not configured" so nothing is
            // sent to an invalid URL.
            val overrideUrl = providers.gradleProperty("PROMPTHAVEN_FUNCTIONS_DEBUG_URL").getOrElse("")
            val projectId = providers.gradleProperty("PROMPTHAVEN_FIREBASE_PROJECT_ID").getOrElse("")
            val debugUrl = when {
                overrideUrl.isNotBlank() -> overrideUrl
                projectId.isNotBlank() -> "http://10.0.2.2:5001/$projectId/us-central1"
                else -> ""
            }
            buildConfigField("String", "PROMPTHAVEN_FUNCTIONS_BASE_URL", "\"$debugUrl\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production URL is supplied at build time via the PROMPTHAVEN_FUNCTIONS_URL
            // Gradle property (a public value, not a secret). It is intentionally left
            // blank by default so no fake/placeholder region-project URL ships in the APK;
            // the AI proxy is simply "not configured" until the deployer sets it.
            val releaseUrl = providers.gradleProperty("PROMPTHAVEN_FUNCTIONS_URL")
                .getOrElse("")
            buildConfigField("String", "PROMPTHAVEN_FUNCTIONS_BASE_URL", "\"$releaseUrl\"")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    // Firebase App Check (Play Integrity provider). The BOM pins versions. No
    // Firebase Auth — alarm interpretation must not require login. These deps
    // compile even without google-services.json; Firebase simply isn't
    // initialized locally, so App Check is skipped and AI degrades to offline.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    // Real org.json for local unit tests (the Android framework's copy is a stub).
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
