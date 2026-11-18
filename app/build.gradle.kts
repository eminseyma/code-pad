plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.codepad.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codepad.app"
        minSdk = 26
        targetSdk = 35
        // Her CI derlemesinde artar; böylece yeni APK eskisinin üzerine güncelleme olarak kurulur.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.0"
    }

    // Sabit imza: telefonda uygulamayı silmeden güncelleyebilmek için her derleme aynı anahtarı kullanır.
    signingConfigs {
        create("codepad") {
            storeFile = file("codepad.keystore")
            storeType = "pkcs12"
            storePassword = "codepad"
            keyAlias = "codepad"
            keyPassword = "codepad"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("codepad")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("codepad")
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
