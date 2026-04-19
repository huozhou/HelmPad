plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

// Version is single-source-of-truth: the git tag (e.g. `v0.1.0`).
// CI passes `-PversionName=0.1.0` (extracted from the tag) into Gradle.
// Local builds without the property fall back to a dev sentinel so the app
// still assembles for testing.
//
// versionCode is derived from versionName so we never have to bump them
// separately. Formula: major*10000 + minor*100 + patch.
// Constraint: minor and patch must each be < 100 to keep monotonicity.
//
// Pre-release suffixes (`1.0.0-alpha.1`, `-beta.2`, `-rc.1`) are preserved in
// versionName but ignored when computing versionCode — so all `1.0.0-*` tags
// share versionCode 10000. That's fine for GitHub Releases (each tag is its
// own asset). If we ever ship to Play Store, every uploaded artifact needs a
// strictly-greater versionCode, so we'd need a prerelease-aware formula then.
val resolvedVersionName: String =
    (findProperty("versionName") as? String)?.takeIf { it.isNotBlank() } ?: "0.0.1-dev"

val resolvedVersionCode: Int = run {
    val core = resolvedVersionName.substringBefore('-')
    val parts = core.split('.').map {
        it.toIntOrNull() ?: error("versionName must be semver MAJOR.MINOR.PATCH (got '$resolvedVersionName')")
    }
    require(parts.size == 3) {
        "versionName must be semver MAJOR.MINOR.PATCH (got '$resolvedVersionName')"
    }
    val (major, minor, patch) = parts
    require(minor in 0..99 && patch in 0..99) {
        "versionName minor and patch must be in 0..99 to keep versionCode monotonic (got '$resolvedVersionName')"
    }
    (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

android {
    namespace = "com.vibepad.keyboard"
    // Compile against SDK 35 because androidx.core 1.15.x mandates it. We intentionally
    // keep targetSdk at 34 — opting into Android 15 runtime behaviour (ART changes,
    // foreground service restrictions, etc.) is a separate decision we'll make when
    // the v1 hardware QA matrix is signed off.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vibepad.keyboard"
        minSdk = 28
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signing is driven by env vars set in CI (see .github/workflows/release.yml).
            // Local `assembleRelease` falls back to debug-signed; never distribute that APK.
            signingConfig = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
                signingConfigs.create("release") {
                    storeFile = file(path)
                    storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                }
            } ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
