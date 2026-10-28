package com.example.solarapporbit

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حد 3 إعلانات بملء الشاشة يوميًا (بتوقيت الجهاز)، مع فاصل أدنى بين إعلانين. يُحتسب الإعلان عند عرضه الفعلي. */
class DailyAdLimiter(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ads_limit", Context.MODE_PRIVATE)

    @Synchronized
    fun canShow(now: Long = System.currentTimeMillis()): Boolean {
        if (countToday(now) >= MAX_PER_DAY) return false
        val last = prefs.getLong(KEY_LAST, 0L)
        return now < last || now - last >= MIN_GAP_MS
    }

    /** يُستدعى عند حدث adDisplayed من SDK فقط. */
    @Synchronized
    fun recordShown(now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_DAY, day(now))
            .putInt(KEY_COUNT, countToday(now) + 1)
            .putLong(KEY_LAST, now)
            .commit()
    }

    private fun countToday(now: Long): Int =
        if (prefs.getString(KEY_DAY, null) == day(now)) prefs.getInt(KEY_COUNT, 0) else 0

    private fun day(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    companion object {
        const val MAX_PER_DAY = 3
        private const val MIN_GAP_MS = 30L * 60 * 1000
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_LAST = "last"
    }
}
