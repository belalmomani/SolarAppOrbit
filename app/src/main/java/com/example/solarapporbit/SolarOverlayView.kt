package com.example.solarapporbit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.PowerManager
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * سطح النظام الشمسي: خلفية شفافة تمامًا، شمس متوهجة، مدارات رفيعة، وكواكب عليها أيقونات التطبيقات.
 * في الشريط السفلي ثلاثة أزرار فقط: تشغيل/إيقاف الدوران، تكبير، تصغير.
 */
class SolarOverlayView(
    context: Context,
    private val listener: Listener
) : View(context) {

    interface Listener {
        fun onZoomIn()
        fun onZoomOut()
        fun onOpenApp(packageName: String)
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** ارتفاع شريط الأزرار أسفل الدائرة (بالبكسل). */
    val bandHeightPx: Int = dp(BAND_DP).roundToInt()
    private val btnR = dp(17f)
    private val btnGap = dp(14f)

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val choreographer = Choreographer.getInstance()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // البيانات
    private var apps: List<OrbitApp> = emptyList()
    private var angles = FloatArray(0)
    private var orbitRadius = FloatArray(0)
    private var planetRadius = FloatArray(0)
    private var sunSprite: Bitmap? = null
    private var planetSprites: List<Bitmap> = emptyList()

    // الهندسة
    private var cx = 0f
    private var cy = 0f
    private var maxR = 0f
    private var sunR = 0f

    // الحالة
    private var rotating = true
    private var active = true
    private var frameScheduled = false
    private var lastFrameNanos = 0L
    private var frameCounter = 0

    // الأدوات
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(90, 255, 255, 255)
    }
    private val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }
    private val btnRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(150, 255, 255, 255)
    }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
        setShadowLayer(dp(3f), 0f, 0f, Color.BLACK)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    // ---------- واجهة عامة ----------

    fun setApps(newApps: List<OrbitApp>) {
        val planets = max(newApps.size - 1, 0)
        if (planets != angles.size) {
            angles = FloatArray(planets) { i -> i * 2.4f } // توزيع أولي متباعد
        }
        apps = newApps
        rebuildSprites()
        invalidate()
        updateAnimation()
    }

    fun setActive(isActive: Boolean) {
        active = isActive
        updateAnimation()
    }

    // ---------- الهندسة والرسوم المسبقة ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildSprites()
    }

    private fun computeGeometry() {
        val solarSize = min(width.toFloat(), (height - bandHeightPx).toFloat())
        cx = width / 2f
        cy = solarSize / 2f
        maxR = solarSize / 2f * 0.97f
        sunR = maxR * 0.17f
        val n = angles.size
        orbitRadius = FloatArray(n) { i ->
            if (n == 1) maxR * 0.6f else maxR * (0.30f + 0.60f * i / (n - 1))
        }
        planetRadius = FloatArray(n) { i -> maxR * (0.0745f - 0.0015f * i) }
        glowPaint.shader = RadialGradient(
            cx, cy, sunR * 2.2f,
            intArrayOf(
                Color.argb(255, 255, 170, 0),
                Color.argb(190, 255, 140, 0),
                Color.argb(70, 255, 100, 0),
                Color.argb(0, 255, 90, 0)
            ),
            floatArrayOf(0f, 0.45f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun rebuildSprites() {
        if (width <= 0 || height <= 0) return
        computeGeometry()
        if (apps.isEmpty()) {
            sunSprite = null
            planetSprites = emptyList()
            return
        }
        sunSprite = makeSphere(apps[0].icon, sunR)
        planetSprites = List(angles.size) { i -> makeSphere(apps[i + 1].icon, planetRadius[i]) }
    }

    /** كرة بإضاءة وظل وحواف: أيقونة التطبيق مقصوصة دائريًا مع تظليل ثلاثي الأبعاد. */
    private fun makeSphere(icon: Bitmap, radius: Float): Bitmap {
        val d = (radius * 2f).roundToInt().coerceAtLeast(4)
        val df = d.toFloat()
        val out = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        c.drawBitmap(icon, Rect(0, 0, icon.width, icon.height), RectF(0f, 0f, df, df), p)

        p.shader = RadialGradient(
            df * 0.36f, df * 0.30f, df * 0.9f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, df, df, p)

        p.shader = RadialGradient(
            df * 0.32f, df * 0.26f, df * 0.34f,
            intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, df, df, p)
        p.shader = null

        // مسح كل ما هو خارج الدائرة
        val outside = Path().apply {
            addCircle(df / 2f, df / 2f, df / 2f, Path.Direction.CW)
            fillType = Path.FillType.INVERSE_WINDING
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        c.drawPath(outside, clear)

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, df * 0.03f)
            color = Color.argb(90, 255, 255, 255)
        }
        c.drawCircle(df / 2f, df / 2f, df / 2f - rim.strokeWidth / 2f, rim)
        return out
    }

    // ---------- الحركة ----------

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        if (!shouldAnimate()) {
            lastFrameNanos = 0L
            return@FrameCallback
        }
        // في وضع توفير الطاقة: تخفيف الحركة إلى نصف معدل الإطارات
        if (powerManager.isPowerSaveMode && (frameCounter++ and 1) == 1) {
            scheduleFrame()
            return@FrameCallback
        }
        if (lastFrameNanos != 0L) {
            val dt = min((frameTimeNanos - lastFrameNanos) / 1_000_000_000f, 0.1f)
            advance(dt)
        }
        lastFrameNanos = frameTimeNanos
        invalidate()
        scheduleFrame()
    }

    private fun shouldAnimate() =
        rotating && active && isAttachedToWindow && angles.isNotEmpty()

    private fun scheduleFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun updateAnimation() {
        if (shouldAnimate()) scheduleFrame()
    }

    private fun advance(dt: Float) {
        for (i in angles.indices) {
            val speed = TWO_PI / (16f + 7f * i) // الأبعد أبطأ
            angles[i] = (angles[i] + speed * dt) % TWO_PI
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
        lastFrameNanos = 0L
        super.onDetachedFromWindow()
    }

    // ---------- الرسم ----------

    private fun planetX(i: Int) = cx + orbitRadius[i] * cos(angles[i])
    private fun planetY(i: Int) = cy + orbitRadius[i] * sin(angles[i])

    override fun onDraw(canvas: Canvas) {
        drawButtons(canvas)
        val sun = sunSprite
        if (sun == null) {
            canvas.drawText(context.getString(R.string.no_usage_data), cx, cy, emptyPaint)
            return
        }
        canvas.drawCircle(cx, cy, sunR * 2.2f, glowPaint)
        for (r in orbitRadius) canvas.drawCircle(cx, cy, r, orbitPaint)
        canvas.drawBitmap(sun, cx - sun.width / 2f, cy - sun.height / 2f, spritePaint)
        for (i in planetSprites.indices) {
            val s = planetSprites[i]
            canvas.drawBitmap(s, planetX(i) - s.width / 2f, planetY(i) - s.height / 2f, spritePaint)
        }
    }

    private fun buttonCx(index: Int) = width / 2f + (index - 1) * (2 * btnR + btnGap)
    private fun buttonCy() = height - bandHeightPx / 2f

    private fun drawButtons(canvas: Canvas) {
        val g = btnR * 0.45f
        val by = buttonCy()
        for (b in 0 until 3) {
            val bx = buttonCx(b)
            canvas.drawCircle(bx, by, btnR, btnFill)
            canvas.drawCircle(bx, by, btnR, btnRing)
            when (b) {
                0 -> if (rotating) { // أيقونة إيقاف مؤقت
                    canvas.drawRect(bx - g * 0.8f, by - g, bx - g * 0.2f, by + g, glyphFill)
                    canvas.drawRect(bx + g * 0.2f, by - g, bx + g * 0.8f, by + g, glyphFill)
                } else { // أيقونة تشغيل
                    val tri = Path().apply {
                        moveTo(bx - g * 0.6f, by - g)
                        lineTo(bx - g * 0.6f, by + g)
                        lineTo(bx + g * 0.9f, by)
                        close()
                    }
                    canvas.drawPath(tri, glyphFill)
                }
                1 -> { // +
                    canvas.drawLine(bx - g, by, bx + g, by, glyphStroke)
                    canvas.drawLine(bx, by - g, bx, by + g, glyphStroke)
                }
                else -> canvas.drawLine(bx - g, by, bx + g, by, glyphStroke) // −
            }
        }
    }

    // ---------- اللمس ----------

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (hypot(event.x - downX, event.y - downY) <= touchSlop) handleTap(event.x, event.y)
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        for (b in 0 until 3) {
            if (hypot(x - buttonCx(b), y - buttonCy()) <= btnR * 1.25f) {
                when (b) {
                    0 -> toggleRotation()
                    1 -> listener.onZoomIn()
                    else -> listener.onZoomOut()
                }
                return
            }
        }
        if (apps.isEmpty()) return
        // الكواكب تُفتح حتى لو كان الدوران متوقفًا
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in angles.indices) {
            val d = hypot(x - planetX(i), y - planetY(i))
            val hit = max(planetRadius[i] * 1.3f, dp(18f))
            if (d <= hit && d < bestDist) {
                best = i
                bestDist = d
            }
        }
        if (best >= 0) {
            listener.onOpenApp(apps[best + 1].packageName)
            return
        }
        if (hypot(x - cx, y - cy) <= sunR * 1.15f) listener.onOpenApp(apps[0].packageName)
    }

    private fun toggleRotation() {
        rotating = !rotating
        invalidate()
        updateAnimation()
    }

    companion object {
        const val BAND_DP = 46f
        private const val TWO_PI = (2.0 * PI).toFloat()
    }
}
