import java.io.File
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Release signing credentials are never committed. They are read from a
 * gitignored `keystore.properties` in the project root, falling back to
 * environment variables. If no credentials are present, a RELEASE build aborts
 * with a clear error (see `gradle.taskGraph.whenReady` below) so we never
 * silently produce an unsigned release. Debug/test builds are unaffected.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingCredential(name: String): String? =
    keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingCredential("RELEASE_STORE_FILE")?.let { File(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    !signingCredential("RELEASE_STORE_PASSWORD").isNullOrBlank() &&
    !signingCredential("RELEASE_KEY_ALIAS").isNullOrBlank() &&
    !signingCredential("RELEASE_KEY_PASSWORD").isNullOrBlank()

// Fail fast (after configuration, before execution) when a release artifact is
// requested but the upload key is not configured. Running debug or unit tests is
// unaffected because they do not include the release bundle/apk tasks.
gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any {
        it.name == "bundleRelease" || it.name == "assembleRelease"
    }
    if (needsReleaseSigning && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Create an upload keystore and set " +
                "RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, " +
                "RELEASE_KEY_PASSWORD in the gitignored keystore.properties (or as environment " +
                "variables), then re-run this build. Aborting instead of producing an unsigned release."
        )
    }
}

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
        applicationId = "com.kemalcetin.aialarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = signingCredential("RELEASE_STORE_PASSWORD")
                keyAlias = signingCredential("RELEASE_KEY_ALIAS")
                keyPassword = signingCredential("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Local developer key (from gitignored local.properties) is compiled
            // into DEBUG builds only, for testing the AI assistant locally.
            val deepSeekKey = localProperties.getProperty("DEEPSEEK_API_KEY", "") ?: ""
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${deepSeekKey.replace("\\", "\\\\")}\"")
        }
        release {
            // A developer API key must never end up in a release artifact. AI is an
            // optional v0.1 enhancement; without a key the app runs on heuristics only.
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // If credentials are missing, signingConfig is left unset here and the
            // gradle.taskGraph.whenReady guard above aborts any release build with a
            // clear message before an unsigned artifact can be produced.
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

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
