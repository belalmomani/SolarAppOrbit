package com.example.solarapporbit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/** يستضيف النافذة العائمة الشفافة ويربطها بإحصاءات الاستخدام والتحكم بالحجم وفتح التطبيقات. */
class OverlayService : Service(), SolarOverlayView.Listener {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: UsageRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private var overlayView: SolarOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var baseSide = 0
    private var minDim = 0
    private var scale = 1f

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            mainHandler.postDelayed(this, REFRESH_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    overlayView?.setActive(false)
                    mainHandler.removeCallbacks(refreshRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    overlayView?.setActive(true)
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.post(refreshRunnable)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = UsageRepository(this)
        startAsForeground()
        if (!Settings.canDrawOverlays(this) || !repository.hasUsageAccess()) {
            stopSelf()
            return
        }
        addOverlay()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        mainHandler.post(refreshRunnable)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun addOverlay() {
        val dm = resources.displayMetrics
        minDim = min(dm.widthPixels, dm.heightPixels)
        baseSide = (minDim * 0.85f).toInt() // حجم ابتدائي ≈ نصف مساحة الشاشة

        val view = SolarOverlayView(this, this)
        val lp = WindowManager.LayoutParams(
            baseSide, baseSide + view.bandHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // لا يأخذ التركيز ولا يحجب اللمس خارج حدوده
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT // شفافية كاملة
        ).apply { gravity = Gravity.CENTER }

        windowManager.addView(view, lp)
        overlayView = view
        params = lp
    }

    private fun refresh() {
        if (!repository.hasUsageAccess()) {
            Toast.makeText(this, R.string.usage_revoked, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        worker.execute {
            val apps = try {
                repository.loadTopApps(MAX_APPS)
            } catch (e: Exception) {
                emptyList()
            }
            mainHandler.post { overlayView?.setApps(apps) }
        }
    }

    // --- SolarOverlayView.Listener ---

    override fun onZoomIn() = changeScale(SCALE_STEP)

    override fun onZoomOut() = changeScale(-SCALE_STEP)

    private fun changeScale(delta: Float) {
        val maxScale = minDim.toFloat() / baseSide // لا يتجاوز عرض الشاشة
        scale = (scale + delta).coerceIn(MIN_SCALE, maxScale)
        val view = overlayView ?: return
        val lp = params ?: return
        val side = (baseSide * scale).toInt()
        lp.width = side
        lp.height = side + view.bandHeightPx
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    override fun onOpenApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) { // حُذف التطبيق أو لم يعد قابلًا للتشغيل
            refresh()
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            refresh()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        runCatching { unregisterReceiver(screenReceiver) }
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val MAX_APPS = 6          // الشمس + 5 كواكب
        private const val REFRESH_MS = 5 * 60 * 1000L
        private const val SCALE_STEP = 0.1f
        private const val MIN_SCALE = 0.5f
    }
}
