package com.example.polaris

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

private typealias M3 = Array<DoubleArray>

/**
 * Apparent topocentric position of Polaris.
 * Pipeline: J2000 catalog position -> proper motion -> precession (IAU 1976)
 *           -> nutation -> annual aberration -> apparent sidereal time
 *           -> alt/az -> atmospheric refraction.
 * Typical error: ~1 arcsecond (geometric), limited mostly by refraction uncertainty.
 */
object Polaris {
    // Hipparcos/ICRS J2000 position and proper motion
    private const val RA0 = 37.95456067      // deg
    private const val DEC0 = 89.26410897     // deg
    private const val PM_RA = 44.48          // mas/yr (already multiplied by cos(dec))
    private const val PM_DEC = -11.85        // mas/yr
    private const val TT_MINUS_UTC = 69.184  // s (32.184 + 37 leap seconds)
    private const val KAPPA = 20.49552 / 206264.806247  // aberration constant, rad

    data class Result(
        val geoAlt: Double, val appAlt: Double, val az: Double,
        val ra: Double, val dec: Double, val last: Double
    )

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0

    private fun rx(a: Double): M3 {
        val c = cos(a); val s = sin(a)
        return arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, c, s), doubleArrayOf(0.0, -s, c))
    }

    private fun rz(a: Double): M3 {
        val c = cos(a); val s = sin(a)
        return arrayOf(doubleArrayOf(c, s, 0.0), doubleArrayOf(-s, c, 0.0), doubleArrayOf(0.0, 0.0, 1.0))
    }

    private fun mm(a: M3, b: M3): M3 = Array(3) { i ->
        DoubleArray(3) { j -> a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j] }
    }

    private fun mv(a: M3, v: DoubleArray) = DoubleArray(3) { i ->
        a[i][0] * v[0] + a[i][1] * v[1] + a[i][2] * v[2]
    }

    fun compute(latDeg: Double, lonDeg: Double, millis: Long): Result {
        val jdUt = millis / 86_400_000.0 + 2440587.5
        val dUt = jdUt - 2451545.0
        val tUt = dUt / 36525.0
        val dTt = dUt + TT_MINUS_UTC / 86400.0
        val t = dTt / 36525.0

        // 1. Proper motion since J2000
        val years = dTt / 365.25
        val ra0 = RA0 + PM_RA / 3.6e6 / cos(rad(DEC0)) * years
        val dec0 = DEC0 + PM_DEC / 3.6e6 * years

        // 2. Precession J2000 -> mean equator of date (rigorous angles)
        val zeta = rad((2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0)
        val z = rad((2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0)
        val theta = rad((2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0)
        val a0 = rad(ra0); val d0 = rad(dec0)
        val pa = cos(d0) * sin(a0 + zeta)
        val pb = cos(theta) * cos(d0) * cos(a0 + zeta) - sin(theta) * sin(d0)
        val pc = sin(theta) * cos(d0) * cos(a0 + zeta) + cos(theta) * sin(d0)
        val raMean = atan2(pa, pb) + z
        val decMean = asin(pc)

        // 3. Nutation (rotation matrix, no pole singularity)
        val eps0 = rad((84381.406 - 46.836769 * t - 0.0001831 * t * t + 0.0020034 * t * t * t) / 3600.0)
        val om = rad(125.04452 - 1934.136261 * t)
        val l = rad(280.4665 + 36000.7698 * t)
        val lp = rad(218.3165 + 481267.8813 * t)
        val dpsi = rad((-17.20 * sin(om) - 1.32 * sin(2 * l) - 0.23 * sin(2 * lp) + 0.21 * sin(2 * om)) / 3600.0)
        val deps = rad((9.20 * cos(om) + 0.57 * cos(2 * l) + 0.10 * cos(2 * lp) - 0.09 * cos(2 * om)) / 3600.0)
        val epsTrue = eps0 + deps
        val nut = mm(rx(-epsTrue), mm(rz(-dpsi), rx(eps0)))

        var p = doubleArrayOf(cos(decMean) * cos(raMean), cos(decMean) * sin(raMean), sin(decMean))
        p = mv(nut, p)

        // 4. Annual aberration (vector form, valid at the pole)
        val l0 = 280.46646 + 36000.76983 * t
        val m = rad(357.52911 + 35999.05029 * t)
        val c = (1.914602 - 0.004817 * t) * sin(m) + 0.019993 * sin(2 * m) + 0.000289 * sin(3 * m)
        val sunLon = rad(l0 + c)
        p[0] += KAPPA * sin(sunLon)
        p[1] += -KAPPA * cos(sunLon) * cos(epsTrue)
        p[2] += -KAPPA * cos(sunLon) * sin(epsTrue)
        val n = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
        val raApp = norm360(deg(atan2(p[1], p[0])))
        val decApp = deg(asin(p[2] / n))

        // 5. Apparent local sidereal time
        val gmst = norm360(280.46061837 + 360.98564736629 * dUt + 0.000387933 * tUt * tUt - tUt * tUt * tUt / 38710000.0)
        val last = norm360(gmst + deg(dpsi) * cos(epsTrue) + lonDeg)

        // 6. Horizontal coordinates
        val h = rad(last - raApp)
        val phi = rad(latDeg)
        val dd = rad(decApp)
        val geoAlt = deg(asin(sin(phi) * sin(dd) + cos(phi) * cos(dd) * cos(h)))
        val az = norm360(deg(atan2(-cos(dd) * sin(h), sin(dd) * cos(phi) - cos(dd) * sin(phi) * cos(h))))

        // 7. Refraction (Saemundsson, standard 1010 hPa / 10 C)
        val refr = if (geoAlt > -1.0) 1.02 / tan(rad(geoAlt + 10.3 / (geoAlt + 5.11))) / 60.0 else 0.0

        return Result(geoAlt, geoAlt + refr, az, raApp, decApp, last)
    }

    fun compass(az: Double): String {
        val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return names[(az / 22.5).roundToInt() % 16]
    }

    fun hms(raDeg: Double): String {
        val h = raDeg / 15.0
        val hh = h.toInt()
        val mm = ((h - hh) * 60).toInt()
        val ss = ((h - hh) * 60 - mm) * 60
        return String.format(Locale.US, "%02dh %02dm %05.2fs", hh, mm, ss)
    }

    /** Azimuth as an offset from north ("E 22' 40.1\"") when within 1 degree, else full D M S. */
    fun azOffset(az: Double): String {
        val off = ((az + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val tenths = (abs(off) * 36000.0).roundToLong()
        if (tenths >= 36000) return dms(az)
        val min = tenths / 600
        val sec = (tenths % 600) / 10.0
        return String.format(Locale.US, "%s %d' %04.1f\"", if (off >= 0) "E" else "W", min, sec)
    }

    fun dms(d: Double, plus: Boolean = false): String {
        val sign = if (d < 0) "-" else if (plus) "+" else ""
        val tenths = (abs(d) * 36000.0).roundToLong()
        val deg = tenths / 36000
        val rem = tenths % 36000
        val min = rem / 600
        val sec = (rem % 600) / 10.0
        return String.format(Locale.US, "%s%d° %02d' %04.1f\"", sign, deg, min, sec)
    }
}

/**
 * Zoomed view looking north. Centre = north celestial pole (altitude = latitude, on the meridian).
 * Vertical line = local meridian. East is to the right. Polaris circles the pole once per sidereal day.
 */
class SkyView(ctx: Context) : View(ctx) {
    private var az = 0.0
    private var geoAlt = 0.0
    private var lat = 0.0
    private var poleDist = 0.6
    private var valid = false

    private val d = resources.displayMetrics.density
    private val bright = Color.rgb(255, 40, 40)
    private val faint = Color.rgb(120, 20, 20)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bright; strokeWidth = 2f * d; style = Paint.Style.STROKE
    }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = faint; strokeWidth = 1.5f * d; style = Paint.Style.STROKE
    }
    private val dash = Paint(thin).apply { pathEffect = DashPathEffect(floatArrayOf(6f * d, 6f * d), 0f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bright; style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bright; typeface = Typeface.MONOSPACE; textSize = 14f * d
    }
    private val dimText = Paint(text).apply { color = Color.rgb(170, 30, 30); textSize = 12f * d }

    fun show(az: Double, geoAlt: Double, lat: Double, poleDistDeg: Double) {
        this.az = az; this.geoAlt = geoAlt; this.lat = lat
        this.poleDist = poleDistDeg.coerceAtLeast(0.05)
        valid = true
        invalidate()
    }

    fun clear() { valid = false; invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2

        c.drawLine(cx, 0f, cx, h, line)      // local meridian
        c.drawLine(0f, cy, w, cy, line)      // east-west line = altitude of the pole (= latitude)
        c.drawText("MERIDIAN", cx + 6 * d, 14 * d, dimText)
        c.drawText("W", 4 * d, cy - 8 * d, text)
        c.drawText("E", w - 16 * d, cy - 8 * d, text)
        if (!valid) return

        val r = min(w * 0.34f, (h - 56 * d) / 2f).coerceAtLeast(20 * d)
        val scale = r / poleDist.toFloat()
        c.drawCircle(cx, cy, r, thin)        // Polaris's orbit around the pole
        c.drawCircle(cx, cy, 3 * d, thin)
        c.drawText("NCP", cx + 8 * d, cy + 16 * d, dimText)

        val dAz = ((az + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val x = dAz * cos(Math.toRadians(geoAlt))   // degrees east of meridian (on sky)
        val y = geoAlt - lat                        // degrees above the pole
        val px = cx + (x * scale).toFloat()
        val py = cy - (y * scale).toFloat()

        c.drawLine(cx, cy, px, py, thin)
        c.drawLine(px, py, cx, py, dash)
        c.drawCircle(px, py, 8 * d, fill)

        val label = "POLARIS"
        val lx = if (px > cx) px - text.measureText(label) - 14 * d else px + 14 * d
        c.drawText(label, lx, py + 5 * d, text)

        c.drawText(
            String.format(Locale.US, "%.1f' %s of meridian", abs(x) * 60, if (x >= 0) "E" else "W"),
            6 * d, h - 22 * d, dimText
        )
        c.drawText(
            String.format(Locale.US, "%.1f' %s pole", abs(y) * 60, if (y >= 0) "above" else "below"),
            6 * d, h - 6 * d, dimText
        )
        val orbit = String.format(Locale.US, "orbit r = %.1f'", poleDist * 60)
        c.drawText(orbit, w - dimText.measureText(orbit) - 6 * d, h - 6 * d, dimText)
    }
}

/**
 * Single-line text that always fills its box: the font size is the largest one where both the
 * text (or the widest expected text, [template]) fits the width and a line fits the height.
 * Does its own measuring, so it behaves the same on every Android version.
 */
class FitTextView(
    ctx: Context,
    private val template: String,
    private val scale: Float = 1f
) : TextView(ctx) {
    private var ready = false
    private val probe = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = false
        ready = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        super.setText(text, type)
        fit()
    }

    private fun fit() {
        if (!ready) return
        val w = (width - paddingLeft - paddingRight) * 0.94f
        val h = (height - paddingTop - paddingBottom) * 0.94f
        if (w <= 0f || h <= 0f) return
        probe.typeface = typeface
        val shown = text?.toString() ?: ""
        var lo = 8f
        var hi = 800f
        repeat(16) {
            val mid = (lo + hi) / 2
            probe.textSize = mid
            val fm = probe.fontMetrics
            val tw = max(probe.measureText(template), probe.measureText(shown))
            if (tw <= w && (fm.descent - fm.ascent) <= h) lo = mid else hi = mid
        }
        val size = lo * scale
        if (abs(textSize - size) > 0.5f) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }
}

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var latIn: EditText
    private lateinit var lonIn: EditText
    private lateinit var altLabel: TextView
    private lateinit var altView: TextView
    private lateinit var azLabel: TextView
    private lateinit var azView: TextView
    private lateinit var info: TextView
    private lateinit var sky: SkyView
    private lateinit var timeLabel: TextView
    private lateinit var timeView: TextView

    private val red = Color.rgb(255, 40, 40)
    private val dim = Color.rgb(170, 30, 30)
    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S 'UTC'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val utc = TimeZone.getTimeZone("UTC")
    private val bigClock = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = utc }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
    private val localFmt = SimpleDateFormat("HH:mm:ss z", Locale.getDefault())

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 100)
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(dim)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
    }

    private fun big(template: String, scale: Float = 1f) = FitTextView(this, template, scale).apply {
        setTextColor(red)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(dim)
        setTextColor(red)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.MONOSPACE
        inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Android 11+ : draw edge to edge and pad by hand. Older phones (Galaxy S8 = Android 7-9):
        // the system already keeps content clear of the bars and resizes for the keyboard.
        val edgeToEdge = Build.VERSION.SDK_INT >= 30
        if (edgeToEdge) WindowCompat.setDecorFitsSystemWindows(window, false)

        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)   // red-on-black preserves night vision
            setPadding(pad, pad, pad, pad)
        }

        latIn = field("Lat (S = -)")
        lonIn = field("Lon (W = -)")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(latIn); addView(lonIn)
        }

        altLabel = label("ALTITUDE")
        altView = big("-88° 88' 88.8\"", 0.8f)
        azLabel = label("AZIMUTH (from north)")
        azView = big("E 88' 88.8\"")
        info = TextView(this).apply {
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
        }

        root.addView(row); root.addView(altLabel); root.addView(altView)
        timeLabel = label("TIME (UTC)")
        timeView = big("88:88:88", 0.65f)
        sky = SkyView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.2f)
        }
        root.addView(azLabel); root.addView(azView)
        root.addView(timeLabel); root.addView(timeView)
        root.addView(sky); root.addView(info)
        setContentView(root)
        if (edgeToEdge) ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val prefs = getSharedPreferences("polaris", MODE_PRIVATE)
        latIn.setText(prefs.getString("lat", ""))
        lonIn.setText(prefs.getString("lon", ""))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit()
                    .putString("lat", latIn.text.toString())
                    .putString("lon", lonIn.text.toString())
                    .apply()
            }
        }
        latIn.addTextChangedListener(watcher)
        lonIn.addTextChangedListener(watcher)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun update() {
        val lat = latIn.text.toString().toDoubleOrNull()
        val lon = lonIn.text.toString().toDoubleOrNull()
        val now = System.currentTimeMillis()
        timeLabel.text = "TIME (UTC)  ${dateFmt.format(Date(now))}"
        timeView.text = bigClock.format(Date(now))
        if (lat == null || lon == null || abs(lat) > 90 || abs(lon) > 180) {
            altView.text = "--"
            azView.text = "--"
            sky.clear()
            info.visibility = View.VISIBLE
            info.text = "Enter latitude (-90..90) and longitude (-180..180)\n${clock.format(Date(now))}"
            return
        }
        val r = Polaris.compute(lat, lon, now)
        altLabel.text = if (r.appAlt > 0) "ALTITUDE (apparent)" else "ALTITUDE - BELOW HORIZON"
        altView.text = Polaris.dms(r.appAlt)
        azLabel.text = "AZIMUTH (from north)  ${Polaris.compass(r.az)}"
        azView.text = Polaris.azOffset(r.az)
        sky.show(r.az, r.geoAlt, lat, 90.0 - r.dec)
        info.visibility = View.GONE
    }
}
