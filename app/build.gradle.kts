plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.solarapporbit"
    compileSdk = 36 // مطلوب من Start.io SDK 5.4.0

    defaultConfig {
        applicationId = "com.example.solarapporbit"
        minSdk = 26
        targetSdk = 35 // راجع متطلبات Google Play الحالية للاستهداف قبل النشر
        versionCode = 1
        versionName = "0.3.3"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Start.io SDK: الإصدار 5.4.0 هو الذي حلّه البناء فعليًا (يتطلب compileSdk 36)
    implementation("com.startapp:inapp-sdk:5.4.0")
}
