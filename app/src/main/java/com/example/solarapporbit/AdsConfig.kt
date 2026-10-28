package com.example.solarapporbit

import android.content.Context
import android.content.pm.ApplicationInfo

object AdsConfig {
    /** معرّف الاختبار الرسمي من مشروع Start.io التجريبي. */
    private const val DEMO_APP_ID = "205489527"

    fun appId(context: Context): String = context.getString(R.string.startio_app_id).trim()

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * الإعلانات تعمل فقط إذا: المعرّف غير فارغ، وإما أن النسخة debug (إعلانات اختبار)
     * أو أن المعرّف حقيقي (ليس معرّف الاختبار).
     */
    fun isEnabled(context: Context): Boolean {
        val id = appId(context)
        if (id.isEmpty()) return false
        return isDebuggable(context) || id != DEMO_APP_ID
    }
}
