import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vexcompany.samvira"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vexcompany.samvira"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Development endpoint: the Android emulator's alias for the host
            // machine running the local backend. Not a production endpoint.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8787/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Only require the production endpoint when a release task is
            // actually requested. Gradle configures all build types even for
            // debug-only tasks, so validating unconditionally would make
            // assembleDebug fail before the debug variant is built.
            val releaseTaskRequested = gradle.startParameter.taskNames.any {
                it.contains("release", ignoreCase = true)
            }
            val configuredApiBaseUrl = providers.gradleProperty("samviraApiBaseUrl")
                .orElse(providers.environmentVariable("SAMVIRA_API_BASE_URL"))
                .orElse("")
                .get()
                .trim()

            if (releaseTaskRequested) {
                require(configuredApiBaseUrl.isNotEmpty()) {
                    "Release builds require -PsamviraApiBaseUrl=https://... or SAMVIRA_API_BASE_URL"
                }
                val parsedApiBaseUrl = runCatching { URI(configuredApiBaseUrl) }.getOrNull()
                require(parsedApiBaseUrl?.scheme == "https" && parsedApiBaseUrl.host != null) {
                    "Release API base URL must be an absolute HTTPS URL"
                }
            }

            val releaseApiBaseUrl = configuredApiBaseUrl.ifEmpty { "https://invalid.invalid/" }
            val escapedApiBaseUrl = releaseApiBaseUrl
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedApiBaseUrl\"")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
