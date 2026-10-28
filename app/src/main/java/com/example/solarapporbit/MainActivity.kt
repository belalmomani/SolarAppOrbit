package com.example.solarapporbit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** شاشة بسيطة: شرح الإذنين، منحهما، وتشغيل/إيقاف النافذة العائمة. */
class MainActivity : Activity() {

    private lateinit var repo: UsageRepository
    private lateinit var overlayStatus: TextView
    private lateinit var usageStatus: TextView
    private lateinit var usageError: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = UsageRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        fun text(res: Int) = TextView(this).apply {
            setText(res); textSize = 16f; setPadding(0, dp(12), 0, dp(4))
        }
        fun button(res: Int, onClick: () -> Unit) = Button(this).apply {
            setText(res); setOnClickListener { onClick() }
        }

        overlayStatus = TextView(this).apply { textSize = 15f }
        usageStatus = TextView(this).apply { textSize = 15f }
        usageError = TextView(this).apply {
            textSize = 15f; setTextColor(0xFFB00020.toInt()); visibility = View.GONE
            setText(R.string.usage_required)
        }

        root.addView(text(R.string.overlay_why))
        root.addView(overlayStatus)
        root.addView(button(R.string.grant_overlay) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(text(R.string.usage_why))
        root.addView(usageStatus)
        root.addView(usageError)
        root.addView(button(R.string.grant_usage) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(button(R.string.start_overlay) { startOverlay() })
        root.addView(button(R.string.stop_overlay) {
            stopService(Intent(this, OverlayService::class.java))
        })

        // من أندرويد 15 تُرسم الواجهة خلف أشرطة النظام، فنطلب إزاحة المحتوى
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = repo.hasUsageAccess()
        overlayStatus.text = getString(
            R.string.overlay_status,
            getString(if (overlayOk) R.string.status_granted else R.string.status_missing)
        )
        usageStatus.text = getString(
            R.string.usage_status,
            getString(if (usageOk) R.string.status_granted else R.string.status_missing)
        )
        if (usageOk) usageError.visibility = View.GONE
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            return
        }
        if (!repo.hasUsageAccess()) {
            usageError.visibility = View.VISIBLE
            Toast.makeText(this, R.string.usage_required, Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
    }
}
