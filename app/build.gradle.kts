plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
            // Non-secret, build-time configurable backend base URL. Debug points
            // at the local Firebase emulator by default; override with the
            // PROMPTHAVEN_FUNCTIONS_DEBUG_URL Gradle property if you need a
            // different emulator/project. The URL is public, never a secret.
            val debugUrl = providers.gradleProperty("PROMPTHAVEN_FUNCTIONS_DEBUG_URL")
                .getOrElse("http://10.0.2.2:5001")
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
