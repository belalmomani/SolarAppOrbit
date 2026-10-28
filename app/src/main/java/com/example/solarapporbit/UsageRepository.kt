package com.example.solarapporbit

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process

/**
 * يقرأ إحصاءات الاستخدام الحقيقية عبر UsageStatsManager (آخر 7 أيام).
 * ملاحظة: نظام أندرويد يحتفظ بالإحصاءات اليومية لفترة محدودة، فقد تكون البيانات أقل من 7 أيام.
 */
class UsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** أكثر [limit] تطبيقات استخدامًا (الأول = الشمس)، مع استبعاد التطبيق نفسه وشاشة الهاتف الرئيسية. */
    fun loadTopApps(limit: Int): List<OrbitApp> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - WINDOW_MS
        val stats = usm.queryAndAggregateUsageStats(begin, end) ?: return emptyList()
        val pm = context.packageManager
        val homePackage = homePackage(pm)

        val result = ArrayList<OrbitApp>()
        val candidates = stats.values
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName != context.packageName && it.packageName != homePackage }
            .sortedByDescending { it.totalTimeInForeground }

        for (s in candidates) {
            if (result.size >= limit) break
            if (pm.getLaunchIntentForPackage(s.packageName) == null) continue // محذوف أو غير قابل للتشغيل
            val icon = try {
                pm.getApplicationIcon(s.packageName).toBitmap(ICON_PX)
            } catch (e: Exception) {
                continue
            }
            result.add(OrbitApp(s.packageName, icon))
        }
        return result
    }

    private fun homePackage(pm: PackageManager): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }

    companion object {
        private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val ICON_PX = 128
    }
}
