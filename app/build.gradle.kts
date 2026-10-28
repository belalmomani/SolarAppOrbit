plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.solarapporbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.solarapporbit"
        minSdk = 26
        targetSdk = 35 // راجع متطلبات Google Play الحالية للاستهداف قبل النشر
        versionCode = 1
        versionName = "0.3.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Start.io SDK: الإصدار بحسب توثيق Start.io (5.+)؛ بعد أول بناء ناجح ثبّت الرقم الفعلي لضمان ثبات النتائج
    implementation("com.startapp:inapp-sdk:5.+")
}
