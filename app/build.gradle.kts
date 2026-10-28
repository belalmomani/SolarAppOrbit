// AGP 9 يتضمن دعم Kotlin مدمجًا، فلا حاجة لتطبيق إضافة kotlin.android
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.solarapporbit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.solarapporbit"
        minSdk = 26
        targetSdk = 36 // راجع متطلبات Google Play الحالية للاستهداف قبل النشر
        versionCode = 1
        versionName = "0.2.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}
