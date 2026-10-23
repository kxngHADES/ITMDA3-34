import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Local builds read SwiftQ/.env (see .env.example). CI has no .env file and supplies the
// same keys as environment variables from the GitHub `dev` / `prod` environment instead.
val dotenv = Properties().apply {
    val file = rootProject.file(".env")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun env(key: String, default: String): String =
    (System.getenv(key) ?: dotenv.getProperty(key))?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: default

android {
    namespace = "com.itmda.swiftq"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.itmda.swiftq"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "APP_ENV", "\"${env("APP_ENV", "local")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${env("API_BASE_URL", "http://10.0.2.2:8000")}\"")
        // Anonymous Firebase sign-in for patients (REST, so no google-services.json is needed).
        buildConfigField("String", "FIREBASE_WEB_API_KEY", "\"${env("FIREBASE_WEB_API_KEY", "")}\"")
        buildConfigField("String", "FIREBASE_AUTH_BASE_URL", "\"${env("FIREBASE_AUTH_BASE_URL", "https://identitytoolkit.googleapis.com")}\"")
        buildConfigField("String", "FIREBASE_TOKEN_BASE_URL", "\"${env("FIREBASE_TOKEN_BASE_URL", "https://securetoken.googleapis.com")}\"")
        // FCM push. The app has no google-services.json (auth is REST), so Firebase is started from these. Blank = push off.
        buildConfigField("String", "FIREBASE_APP_ID", "\"${env("FIREBASE_APP_ID", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${env("FIREBASE_PROJECT_ID", "")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${env("FIREBASE_SENDER_ID", "")}\"")
        // Dev API has no /v1/sync/push yet, so the outbox sends joins through the REST endpoint.
        buildConfigField("boolean", "SYNC_PUSH_ENABLED", "${env("SYNC_PUSH_ENABLED", "false")}")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        // Languages are switched in-app, so every locale has to ship in the base split.
        language {
            enableSplit = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}