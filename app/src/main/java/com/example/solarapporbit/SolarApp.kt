package com.example.solarapporbit

import android.app.Application
import com.startapp.sdk.adsbase.StartAppSDK

class SolarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!AdsConfig.isEnabled(this)) return
        // إعلانات اختبار في نسخ debug فقط
        if (AdsConfig.isDebuggable(this)) StartAppSDK.setTestAdsEnabled(true)
        StartAppSDK.initParams(this, AdsConfig.appId(this))
            .setReturnAdsEnabled(false) // لا إعلانات عند العودة للتطبيق
            .init()
    }
}
