package com.example.solarapporbit

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * إعلان Interstitial واحد داخل شاشة التطبيق الرئيسية فقط (ليس فوق تطبيقات أخرى
 * وليس جزءًا من النافذة العائمة). يُعرض قبل تشغيل النافذة العائمة، ثم تتابع الوظيفة.
 * إذا لم يتوفر إعلان أو بلغ الحد اليومي: تُتابع الوظيفة فورًا دون أي تعطيل.
 */
class InterstitialController(private val activity: Activity) {

    private val limiter = DailyAdLimiter(activity)
    private val handler = Handler(Looper.getMainLooper())
    private var ad: StartAppAd? = null
    private var ready = false
    private var loading = false
    private var showing = false

    fun preload() {
        if (!AdsConfig.isEnabled(activity) || ready || loading || showing || !limiter.canShow()) return
        loading = true
        val candidate = StartAppAd(activity)
        candidate.loadAd(object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                loading = false
                this@InterstitialController.ad = candidate
                ready = true
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                loading = false // لا نعيد المحاولة فورًا؛ ستُجرّب عند العودة للشاشة
            }
        })
    }

    /** يعرض الإعلان إن كان جاهزًا ومسموحًا، ثم يستدعي [next] مرة واحدة في كل الأحوال. */
    fun showThen(next: () -> Unit) {
        val current = ad
        if (!AdsConfig.isEnabled(activity) || !ready || current == null || showing || !limiter.canShow()) {
            next()
            return
        }
        showing = true
        ready = false
        ad = null

        var finished = false
        var displayed = false
        fun finish() {
            if (finished) return
            finished = true
            showing = false
            handler.removeCallbacksAndMessages(null)
            next()
        }

        current.showAd(object : AdDisplayListener {
            override fun adDisplayed(ad: Ad) {
                displayed = true
                limiter.recordShown() // الاحتساب عند العرض الفعلي
            }

            override fun adHidden(ad: Ad) = finish()
            override fun adClicked(ad: Ad) {}
            override fun adNotDisplayed(ad: Ad) = finish()
        })
        // شبكة أمان: إن لم يصل أي حدث عرض خلال 5 ثوانٍ، تابع دون تعطيل
        handler.postDelayed({ if (!displayed) finish() }, 5000)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        ad = null
    }
}
