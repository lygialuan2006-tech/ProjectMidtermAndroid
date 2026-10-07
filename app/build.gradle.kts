plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.project_gk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.project_gk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // UI cơ bản
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.activity.ktx)

    // 1. Xác thực sinh trắc học (Vân tay / Face ID)
    implementation("androidx.biometric:biometric:1.1.0")

    // 2. Android Keystore & EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 3. Room Database (Java dùng annotationProcessor)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // 4. SQLCipher for Android
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
