plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La signature Release n'existe que si les quatre variables sont fournies par la CI.
val keystorePath: String? = System.getenv("RX_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("RX_KEYSTORE_PASSWORD")
val keyAliasName: String? = System.getenv("RX_KEY_ALIAS")
val keyAliasPassword: String? = System.getenv("RX_KEY_PASSWORD")
val hasSigning = listOf(keystorePath, keystorePassword, keyAliasName, keyAliasPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.ramxtreme.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ramxtreme.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = (System.getenv("GITHUB_REF_NAME")
            ?.takeIf { it.matches(Regex("v\\d+\\.\\d+\\.\\d+.*")) }
            ?.removePrefix("v")) ?: "1.0.0-dev"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyAliasPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 désactivé : le service Shizuku est instancié par réflexion.
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    lint { abortOnError = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val shizuku = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku")
    implementation("dev.rikka.shizuku:provider:$shizuku")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
