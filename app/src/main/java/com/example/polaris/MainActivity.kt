package com.example.polaris

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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

/** Widest altitude/azimuth text; both readouts size their font from this so they match. */
private const val DMS_TEMPLATE = "-188° 88' 88.88\""

/** A catalogue star: ICRS J2000 position (deg), proper motion (mas/yr, RA already x cos dec) and parallax (mas). */
class Star(
    val name: String, val bayer: String, val mag: Double,
    val ra: Double, val dec: Double, val pmRa: Double, val pmDec: Double, val plx: Double
)

private fun star(
    name: String, bayer: String, mag: Double,
    rh: Int, rm: Int, rs: Double, dd: Int, dm: Int, ds: Double,
    pmRa: Double, pmDec: Double, plx: Double
): Star {
    val ra = (rh + rm / 60.0 + rs / 3600.0) * 15.0
    val sign = if (dd < 0) -1.0 else 1.0
    val dec = sign * (abs(dd) + dm / 60.0 + ds / 3600.0)
    return Star(name, bayer, mag, ra, dec, pmRa, pmDec, plx)
}

/** Polaris first, then the 24 brightest stars that can be seen from northern latitudes. */
private val STARS = listOf(
    star("Polaris", "α UMi", 1.98, 2, 31, 49.0945, 89, 15, 50.7926, 44.48, -11.85, 7.54),
    star("Sirius", "α CMa", -1.46, 6, 45, 8.917, -16, 42, 58.02, -546.01, -1223.07, 379.21),
    star("Arcturus", "α Boo", -0.05, 14, 15, 39.672, 19, 10, 56.67, -1093.39, -2000.06, 88.83),
    star("Vega", "α Lyr", 0.03, 18, 36, 56.336, 38, 47, 1.29, 200.94, 286.23, 130.23),
    star("Capella", "α Aur", 0.08, 5, 16, 41.359, 45, 59, 52.77, 75.52, -427.11, 76.20),
    star("Rigel", "β Ori", 0.13, 5, 14, 32.272, -8, 12, 5.90, 1.31, 0.50, 3.78),
    star("Procyon", "α CMi", 0.34, 7, 39, 18.119, 5, 13, 29.96, -714.59, -1036.80, 284.56),
    star("Betelgeuse", "α Ori", 0.42, 5, 55, 10.305, 7, 24, 25.43, 27.54, 11.30, 6.55),
    star("Altair", "α Aql", 0.76, 19, 50, 46.999, 8, 52, 5.96, 536.23, 385.29, 194.95),
    star("Aldebaran", "α Tau", 0.86, 4, 35, 55.239, 16, 30, 33.49, 63.45, -188.94, 48.94),
    star("Spica", "α Vir", 0.97, 13, 25, 11.579, -11, 9, 40.75, -42.35, -30.67, 12.44),
    star("Antares", "α Sco", 1.06, 16, 29, 24.460, -26, 25, 55.21, -12.11, -23.30, 5.89),
    star("Pollux", "β Gem", 1.14, 7, 45, 18.950, 28, 1, 34.32, -625.69, -45.95, 96.54),
    star("Fomalhaut", "α PsA", 1.16, 22, 57, 39.046, -29, 37, 20.05, 329.22, -164.22, 129.81),
    star("Deneb", "α Cyg", 1.25, 20, 41, 25.916, 45, 16, 49.22, 1.56, 1.55, 2.31),
    star("Regulus", "α Leo", 1.35, 10, 8, 22.311, 11, 58, 1.95, -249.40, 4.91, 41.13),
    star("Castor", "α Gem", 1.58, 7, 34, 35.873, 31, 53, 17.82, -206.33, -148.18, 64.12),
    star("Bellatrix", "γ Ori", 1.64, 5, 25, 7.863, 6, 20, 58.93, -8.75, -13.28, 12.92),
    star("Elnath", "β Tau", 1.65, 5, 26, 17.513, 28, 36, 26.83, 23.28, -174.22, 24.89),
    star("Alnilam", "ε Ori", 1.69, 5, 36, 12.813, -1, 12, 6.91, 1.49, -1.06, 1.65),
    star("Alnitak", "ζ Ori", 1.77, 5, 40, 45.527, -1, 56, 33.26, 3.99, 2.54, 3.99),
    star("Alioth", "ε UMa", 1.77, 12, 54, 1.743, 55, 57, 35.36, 111.74, -8.99, 40.30),
    star("Dubhe", "α UMa", 1.79, 11, 3, 43.672, 61, 45, 3.72, -136.46, -35.25, 26.38),
    star("Mirfak", "α Per", 1.79, 3, 24, 19.370, 49, 51, 40.25, 24.11, -26.01, 6.44),
    star("Wezen", "δ CMa", 1.83, 7, 8, 23.484, -26, 23, 35.52, -2.75, 3.33, 1.82)
)

/** Something you can point at. body: 0 = catalogue star, 1 = Sun, 2 = Moon, 3..9 = Mercury..Neptune. */
class Target(val name: String, val sub: String, val star: Star?, val body: Int)

/** Swipe order: Polaris, Sun, Moon, the planets, then the other 24 stars. */
private val TARGETS: List<Target> = listOf(Target("Polaris", STARS[0].bayer, STARS[0], 0)) + listOf(
    Target("Sun", "Sun", null, 1),
    Target("Moon", "Moon", null, 2),
    Target("Mercury", "Planet", null, 3),
    Target("Venus", "Planet", null, 4),
    Target("Mars", "Planet", null, 5),
    Target("Jupiter", "Planet", null, 6),
    Target("Saturn", "Planet", null, 7),
    Target("Uranus", "Planet", null, 8),
    Target("Neptune", "Planet", null, 9)
) + STARS.drop(1).map { Target(it.name, it.bayer, it, 0) }

/**
 * Apparent topocentric position of a star, the Sun, the Moon or a planet.
 * Pipeline: J2000 catalog position -> proper motion -> precession (IAU 1976)
 *           -> nutation -> annual aberration -> apparent sidereal time
 *           -> alt/az -> atmospheric refraction.
 * Typical error: ~1 arcsecond (geometric), limited mostly by refraction uncertainty.
 */
object Astro {
    private const val TT_MINUS_UTC = 69.184  // s (32.184 + 37 leap seconds)
    private const val KAPPA = 20.49552 / 206264.806247  // aberration constant, rad
    private const val AU_KM = 149597870.7
    private const val OBLIQUITY_J2000 = 23.43928        // deg

    // Planet -> row in ELEMENTS (index = Target.body; 3 Mercury, 4 Venus, 5 Mars, ... 9 Neptune)
    private val PLANET_ROW = intArrayOf(0, 0, 0, 0, 1, 3, 4, 5, 6, 7)

    // JPL "approximate positions of the major planets" (Standish), valid 1800-2050.
    // Rows: Mercury, Venus, Earth-Moon barycentre, Mars, Jupiter, Saturn, Uranus, Neptune.
    // a, da, e, de, I, dI, L, dL, long.peri, d, long.node, d, b, c, s, f   (per Julian century)
    private val ELEMENTS = arrayOf(
        doubleArrayOf(0.38709927, 3.7e-07, 0.20563593, 1.906e-05, 7.00497902, -0.00594749, 252.2503235, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.72333566, 3.9e-06, 0.00677672, -4.107e-05, 3.39467605, -0.0007889, 181.9790995, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(1.00000261, 5.62e-06, 0.01671123, -4.392e-05, -1.531e-05, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(1.52371034, 1.847e-05, 0.0933941, 7.882e-05, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(5.202887, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106, -0.00012452, 0.0606406, -0.35635438, 38.35125),
        doubleArrayOf(9.53667594, -0.0012506, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794, 0.00025899, -0.13434469, 0.87320147, 38.35125),
        doubleArrayOf(19.18916464, -0.00196176, 0.04725744, -4.397e-05, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.9542763, 0.40805281, 74.01692503, 0.04240589, 0.00058331, -0.97731848, 0.17689245, 7.67025),
        doubleArrayOf(30.06992276, 0.00026291, 0.00859048, 5.105e-05, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664, -0.00041348, 0.68346318, -0.10162547, 7.67025)
    )

    // Moon, Meeus ch. 47 (main terms). D, M, M', F, sum_l (1e-6 deg), sum_r (1e-3 km)
    private val MOON_LR = arrayOf(
        intArrayOf(0, 0, 1, 0, 6288774, -20905355),
        intArrayOf(2, 0, -1, 0, 1274027, -3699111),
        intArrayOf(2, 0, 0, 0, 658314, -2955968),
        intArrayOf(0, 0, 2, 0, 213618, -569925),
        intArrayOf(0, 1, 0, 0, -185116, 48888),
        intArrayOf(0, 0, 0, 2, -114332, -3149),
        intArrayOf(2, 0, -2, 0, 58793, 246158),
        intArrayOf(2, -1, -1, 0, 57066, -152138),
        intArrayOf(2, 0, 1, 0, 53322, -170733),
        intArrayOf(2, -1, 0, 0, 45758, -204586),
        intArrayOf(0, 1, -1, 0, -40923, -129620),
        intArrayOf(1, 0, 0, 0, -34720, 108743),
        intArrayOf(0, 1, 1, 0, -30383, 104755),
        intArrayOf(2, 0, 0, -2, 15327, 10321),
        intArrayOf(0, 0, 1, 2, -12528, 0),
        intArrayOf(0, 0, 1, -2, 10980, 79661),
        intArrayOf(4, 0, -1, 0, 10675, -34782),
        intArrayOf(0, 0, 3, 0, 10034, -23210),
        intArrayOf(4, 0, -2, 0, 8548, -21636),
        intArrayOf(2, 1, -1, 0, -7888, 24208),
        intArrayOf(2, 1, 0, 0, -6766, 30824),
        intArrayOf(1, 0, -1, 0, -5163, -8379),
        intArrayOf(1, 1, 0, 0, 4987, -16675),
        intArrayOf(2, -1, 1, 0, 4036, -12831),
        intArrayOf(2, 0, 2, 0, 3994, -10445),
        intArrayOf(4, 0, 0, 0, 3861, -11650),
        intArrayOf(2, 0, -3, 0, 3665, 14403),
        intArrayOf(0, 1, -2, 0, -2689, -7003),
        intArrayOf(2, 0, -1, 2, -2602, 0),
        intArrayOf(2, -1, -2, 0, 2390, 10056)
    )

    // D, M, M', F, sum_b (1e-6 deg)
    private val MOON_B = arrayOf(
        intArrayOf(0, 0, 0, 1, 5128122),
        intArrayOf(0, 0, 1, 1, 280602),
        intArrayOf(0, 0, 1, -1, 277693),
        intArrayOf(2, 0, 0, -1, 173237),
        intArrayOf(2, 0, -1, 1, 55413),
        intArrayOf(2, 0, -1, -1, 46271),
        intArrayOf(2, 0, 0, 1, 32573),
        intArrayOf(0, 0, 2, 1, 17198),
        intArrayOf(2, 0, 1, -1, 9266),
        intArrayOf(0, 0, 2, -1, 8822),
        intArrayOf(2, -1, 0, -1, 8216),
        intArrayOf(2, 0, -2, -1, 4324),
        intArrayOf(2, 0, 1, 1, 4200),
        intArrayOf(2, 1, 0, -1, -3359),
        intArrayOf(2, -1, -1, 1, 2463),
        intArrayOf(2, -1, 0, 1, 2211),
        intArrayOf(2, -1, -1, -1, 2065),
        intArrayOf(0, 1, -1, -1, -1870),
        intArrayOf(4, 0, -1, -1, 1828),
        intArrayOf(0, 1, 0, 1, -1794),
        intArrayOf(0, 0, 0, 3, -1749),
        intArrayOf(0, 1, -1, 1, -1565),
        intArrayOf(1, 0, 0, 1, -1491),
        intArrayOf(0, 1, 1, 1, -1475),
        intArrayOf(0, 1, 1, -1, -1410),
        intArrayOf(0, 1, 0, -1, -1344),
        intArrayOf(1, 0, 0, -1, -1335),
        intArrayOf(0, 0, 3, 1, 1107),
        intArrayOf(4, 0, 0, -1, 1021),
        intArrayOf(4, 0, -1, 1, 833)
    )

    data class Result(
        val geoAlt: Double, val appAlt: Double, val az: Double,
        val ra: Double, val dec: Double, val last: Double,
        val dist: Double = 0.0,      // AU, solar-system bodies only
        val illum: Double = -1.0     // lit fraction of the Moon, 0..1
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

    private fun precess(a0: Double, d0: Double, t: Double): DoubleArray {
        val zeta = rad((2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0)
        val z = rad((2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0)
        val theta = rad((2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0)
        val pa = cos(d0) * sin(a0 + zeta)
        val pb = cos(theta) * cos(d0) * cos(a0 + zeta) - sin(theta) * sin(d0)
        val pc = sin(theta) * cos(d0) * cos(a0 + zeta) + cos(theta) * sin(d0)
        return doubleArrayOf(atan2(pa, pb) + z, asin(pc))
    }

    private fun eclToEq(lamDeg: Double, betDeg: Double, dist: Double, eps: Double): DoubleArray {
        val x = cos(rad(betDeg)) * cos(rad(lamDeg))
        val y = cos(rad(betDeg)) * sin(rad(lamDeg))
        val z = sin(rad(betDeg))
        return doubleArrayOf(dist * x, dist * (y * cos(eps) - z * sin(eps)), dist * (y * sin(eps) + z * cos(eps)))
    }

    /** Heliocentric position, J2000 equatorial, AU. */
    private fun planetHelio(row: Int, t: Double): DoubleArray {
        val k = ELEMENTS[row]
        val a = k[0] + k[1] * t
        val e = k[2] + k[3] * t
        val inc = rad(k[4] + k[5] * t)
        val meanLon = k[6] + k[7] * t
        val wbar = k[8] + k[9] * t
        val node = rad(k[10] + k[11] * t)
        var mean = meanLon - wbar + k[12] * t * t + k[13] * cos(rad(k[15] * t)) + k[14] * sin(rad(k[15] * t))
        mean = ((mean + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val mr = rad(mean)
        var ea = mr + e * sin(mr)
        repeat(10) { ea += (mr - (ea - e * sin(ea))) / (1 - e * cos(ea)) }
        val xp = a * (cos(ea) - e)
        val yp = a * sqrt(1 - e * e) * sin(ea)
        val w = rad(wbar) - node
        val cw = cos(w); val sw = sin(w)
        val co = cos(node); val so = sin(node)
        val ci = cos(inc); val si = sin(inc)
        val x = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp
        val y = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp
        val zz = (sw * si) * xp + (cw * si) * yp
        val ob = rad(OBLIQUITY_J2000)
        return doubleArrayOf(x, cos(ob) * y - sin(ob) * zz, sin(ob) * y + cos(ob) * zz)
    }

    /** Geocentric Moon: ecliptic longitude and latitude of date (deg) and distance (km). */
    private fun moon(t: Double): DoubleArray {
        val lm = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t + t * t * t / 538841.0 - t * t * t * t / 65194000.0)
        val dm = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t + t * t * t / 545868.0 - t * t * t * t / 113065000.0)
        val ms = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t * t * t / 24490000.0)
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t + t * t * t / 69699.0 - t * t * t * t / 14712000.0)
        val fm = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t - t * t * t / 3526000.0 + t * t * t * t / 863310000.0)
        val ecc = 1 - 0.002516 * t - 0.0000074 * t * t
        var sl = 0.0
        var sr = 0.0
        var sb = 0.0
        for (q in MOON_LR) {
            val arg = rad(q[0] * dm + q[1] * ms + q[2] * mp + q[3] * fm)
            val ef = when (abs(q[1])) { 1 -> ecc; 2 -> ecc * ecc; else -> 1.0 }
            sl += q[4] * ef * sin(arg)
            sr += q[5] * ef * cos(arg)
        }
        for (q in MOON_B) {
            val arg = rad(q[0] * dm + q[1] * ms + q[2] * mp + q[3] * fm)
            val ef = when (abs(q[1])) { 1 -> ecc; 2 -> ecc * ecc; else -> 1.0 }
            sb += q[4] * ef * sin(arg)
        }
        val a1 = rad(119.75 + 131.849 * t)
        val a2 = rad(53.09 + 479264.290 * t)
        val a3 = rad(313.45 + 481266.484 * t)
        sl += 3958 * sin(a1) + 1962 * sin(rad(lm - fm)) + 318 * sin(a2)
        sb += -2235 * sin(rad(lm)) + 382 * sin(a3) + 175 * sin(a1 - rad(fm)) + 175 * sin(a1 + rad(fm)) +
                127 * sin(rad(lm - mp)) - 115 * sin(rad(lm + mp))
        return doubleArrayOf(norm360(lm + sl / 1e6), sb / 1e6, 385000.56 + sr / 1000.0)
    }

    /** Earth's heliocentric position, J2000 equatorial, AU (Earth-Moon barycentre minus the Moon's pull). */
    private fun earthHelio(t: Double, eps: Double): DoubleArray {
        val emb = planetHelio(2, t)
        val mo = moon(t)
        val mv = eclToEq(mo[0], mo[1], mo[2] / AU_KM, eps)
        val f = 1.0 / 82.30056
        return doubleArrayOf(emb[0] - mv[0] * f, emb[1] - mv[1] * f, emb[2] - mv[2] * f)
    }

    fun compute(target: Target, latDeg: Double, lonDeg: Double, millis: Long): Result {
        val jdUt = millis / 86_400_000.0 + 2440587.5
        val dUt = jdUt - 2451545.0
        val tUt = dUt / 36525.0
        val dTt = dUt + TT_MINUS_UTC / 86400.0
        val t = dTt / 36525.0
        val masToRad = 4.84813681109536e-9
        val eps0 = rad((84381.406 - 46.836769 * t - 0.0001831 * t * t + 0.0020034 * t * t * t) / 3600.0)

        // 1. Mean place of date: direction and (solar system only) distance
        val raMean: Double
        val decMean: Double
        var distAu = 0.0
        var moonLam = 0.0
        var moonBet = 0.0
        val st = target.star
        if (st != null) {
            // Star: proper motion (tangent-plane vector form), then precession
            val years = dTt / 365.25
            val sa = sin(rad(st.ra)); val ca = cos(rad(st.ra))
            val sd = sin(rad(st.dec)); val cd = cos(rad(st.dec))
            val mua = st.pmRa * masToRad * years
            val mud = st.pmDec * masToRad * years
            val sx = cd * ca - mua * sa - mud * sd * ca
            val sy = cd * sa + mua * ca - mud * sd * sa
            val sz = sd + mud * cd
            val pr = precess(atan2(sy, sx), atan2(sz, sqrt(sx * sx + sy * sy)), t)
            raMean = pr[0]; decMean = pr[1]
        } else if (target.body == 2) {
            // Moon: series gives the position of date; step back one light-time
            var mo = moon(t)
            val tau = mo[2] / 299792.458 / 86400.0
            mo = moon(t - tau / 36525.0)
            moonLam = mo[0]; moonBet = mo[1]
            val v = eclToEq(mo[0], mo[1], 1.0, eps0)
            raMean = atan2(v[1], v[0]); decMean = asin(v[2])
            distAu = mo[2] / AU_KM
        } else {
            val earth = earthHelio(t, eps0)
            var g = doubleArrayOf(-earth[0], -earth[1], -earth[2])    // Sun, geocentric
            distAu = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
            if (target.body >= 3) {
                val row = PLANET_ROW[target.body]
                var tau = 0.0
                repeat(3) {                                             // light-time iteration
                    val ph = planetHelio(row, t - tau / 36525.0)
                    g = doubleArrayOf(ph[0] - earth[0], ph[1] - earth[1], ph[2] - earth[2])
                    distAu = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
                    tau = distAu / 173.1446327
                }
            }
            val pr = precess(atan2(g[1], g[0]), atan2(g[2], hypot(g[0], g[1])), t)
            raMean = pr[0]; decMean = pr[1]
        }

        // 2. Nutation (rotation matrix, no pole singularity)
        val om = rad(125.04452 - 1934.136261 * t)
        val l = rad(280.4665 + 36000.7698 * t)
        val lp = rad(218.3165 + 481267.8813 * t)
        val dpsi = rad((-17.20 * sin(om) - 1.32 * sin(2 * l) - 0.23 * sin(2 * lp) + 0.21 * sin(2 * om)) / 3600.0)
        val deps = rad((9.20 * cos(om) + 0.57 * cos(2 * l) + 0.10 * cos(2 * lp) - 0.09 * cos(2 * om)) / 3600.0)
        val epsTrue = eps0 + deps
        val nut = mm(rx(-epsTrue), mm(rz(-dpsi), rx(eps0)))

        var p = doubleArrayOf(cos(decMean) * cos(raMean), cos(decMean) * sin(raMean), sin(decMean))
        p = mv(nut, p)

        // 3. Annual aberration (vector form, valid at the pole)
        val l0 = 280.46646 + 36000.76983 * t
        val m = rad(357.52911 + 35999.05029 * t)
        val c = (1.914602 - 0.004817 * t) * sin(m) + 0.019993 * sin(2 * m) + 0.000289 * sin(3 * m)
        val sunLon = rad(l0 + c)
        p[0] += KAPPA * sin(sunLon)
        p[1] += -KAPPA * cos(sunLon) * cos(epsTrue)
        p[2] += -KAPPA * cos(sunLon) * sin(epsTrue)

        // 3b. Annual parallax (stars) and Sun distance (Moon phase)
        val ecc = 0.016708634 - 0.000042037 * t
        val nu = m + rad(c)
        val rAu = 1.000001018 * (1 - ecc * ecc) / (1 + ecc * cos(nu))
        if (st != null) {
            val plx = st.plx * masToRad
            p[0] += plx * rAu * cos(sunLon)
            p[1] += plx * rAu * sin(sunLon) * cos(epsTrue)
            p[2] += plx * rAu * sin(sunLon) * sin(epsTrue)
        }
        val n = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])

        // 4. Apparent local sidereal time
        val gmst = norm360(280.46061837 + 360.98564736629 * dUt + 0.000387933 * tUt * tUt - tUt * tUt * tUt / 38710000.0)
        val last = norm360(gmst + deg(dpsi) * cos(epsTrue) + lonDeg)

        // 5. Topocentric parallax for the Sun, Moon and planets (observer on the Earth's surface)
        var tx = p[0] / n
        var ty = p[1] / n
        var tz = p[2] / n
        if (distAu > 0.0) {
            val u = atan(0.99664719 * tan(rad(latDeg)))
            val rhoSin = 0.99664719 * sin(u)
            val rhoCos = cos(u)
            val reAu = 6378.14 / AU_KM
            val lstR = rad(last)
            tx = tx * distAu - reAu * rhoCos * cos(lstR)
            ty = ty * distAu - reAu * rhoCos * sin(lstR)
            tz = tz * distAu - reAu * rhoSin
        }
        val tn = sqrt(tx * tx + ty * ty + tz * tz)
        val raApp = norm360(deg(atan2(ty, tx)))
        val decApp = deg(asin(tz / tn))

        // 6. Horizontal coordinates
        val h = rad(last - raApp)
        val phi = rad(latDeg)
        val dd = rad(decApp)
        val geoAlt = deg(asin(sin(phi) * sin(dd) + cos(phi) * cos(dd) * cos(h)))
        val az = norm360(deg(atan2(-cos(dd) * sin(h), sin(dd) * cos(phi) - cos(dd) * sin(phi) * cos(h))))

        // 7. Refraction (Saemundsson, standard 1010 hPa / 10 C)
        val refr = if (geoAlt > -1.0) 1.02 / tan(rad(geoAlt + 10.3 / (geoAlt + 5.11))) / 60.0 else 0.0

        // 8. Moon: lit fraction from the phase angle
        var illum = -1.0
        if (target.body == 2) {
            val psi = acos(cos(rad(moonBet)) * cos(rad(moonLam) - sunLon))
            val phaseAngle = atan2(rAu * sin(psi), distAu - rAu * cos(psi))
            illum = (1 + cos(phaseAngle)) / 2
        }

        return Result(geoAlt, geoAlt + refr, az, raApp, decApp, last, distAu, illum)
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

    /** Azimuth as an offset east or west of north, e.g. "E  0° 22' 40.08"" */
    fun azOffset(az: Double): String {
        val off = ((az + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return dmsFixed(if (off >= 0) 'E' else 'W', off)
    }

    /** Fixed-width D M S (1 prefix char + 3-wide degrees) so altitude and azimuth line up. */
    fun dmsFixed(prefix: Char, value: Double): String {
        val hund = (abs(value) * 360000.0).roundToLong()
        val deg = hund / 360000
        val rem = hund % 360000
        val min = rem / 6000
        val sec = (rem % 6000) / 100.0
        return String.format(Locale.US, "%s%3d° %02d' %05.2f\"", prefix, deg, min, sec)
    }

    fun dms(d: Double, plus: Boolean = false): String {
        val sign = if (d < 0) "-" else if (plus) "+" else ""
        val hund = (abs(d) * 360000.0).roundToLong()
        val deg = hund / 360000
        val rem = hund % 360000
        val min = rem / 6000
        val sec = (rem % 6000) / 100.0
        return String.format(Locale.US, "%s%d° %02d' %05.2f\"", sign, deg, min, sec)
    }
}

/**
 * Chart. Polaris: zoomed view looking north (centre = north celestial pole, vertical line = meridian,
 * east to the right). Any other star: whole sky looking straight up (centre = zenith, north at the
 * top, east to the left, rings at 30 and 60 degrees altitude).
 */
class SkyView(ctx: Context) : View(ctx) {
    private var allSky = false
    private var name = ""
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

    /** Polaris: zoomed view around the pole. */
    fun showPole(az: Double, geoAlt: Double, lat: Double, poleDistDeg: Double) {
        allSky = false
        this.az = az; this.geoAlt = geoAlt; this.lat = lat
        this.poleDist = poleDistDeg.coerceAtLeast(0.05)
        valid = true
        invalidate()
    }

    /** Any other star: whole-sky view. */
    fun showAllSky(name: String, az: Double, geoAlt: Double) {
        allSky = true
        this.name = name; this.az = az; this.geoAlt = geoAlt
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
        c.drawLine(0f, cy, w, cy, line)      // east-west line
        c.drawText("MERIDIAN", cx + 6 * d, 14 * d, dimText)
        c.drawText(if (allSky) "E" else "W", 4 * d, cy - 8 * d, text)
        c.drawText(if (allSky) "W" else "E", w - 16 * d, cy - 8 * d, text)
        if (!valid) return
        if (allSky) drawAllSky(c, w, h, cx, cy) else drawPole(c, w, h, cx, cy)
    }

    private fun drawAllSky(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        c.drawText("N", cx - 14 * d, 14 * d, text)
        c.drawText("S", cx - 14 * d, h - 4 * d, text)
        val rh = min(w / 2f - 22 * d, h / 2f - 22 * d).coerceAtLeast(20 * d)   // horizon radius
        c.drawCircle(cx, cy, rh, line)             // horizon
        c.drawCircle(cx, cy, rh * 2f / 3f, thin)   // 30 degrees altitude
        c.drawCircle(cx, cy, rh / 3f, thin)        // 60 degrees altitude
        c.drawCircle(cx, cy, 3 * d, thin)          // zenith

        val rr = ((90.0 - geoAlt) / 90.0 * rh).toFloat().coerceIn(0f, rh * 1.12f)
        val a = Math.toRadians(az)
        val sx = cx - (rr * sin(a)).toFloat()      // east is to the left when looking up
        val sy = cy - (rr * cos(a)).toFloat()
        c.drawCircle(sx, sy, 8 * d, if (geoAlt >= 0) fill else line)

        val label = name.uppercase()
        val lw = text.measureText(label)
        val lx = if (sx > cx) sx - lw - 14 * d else sx + 14 * d
        c.drawText(label, lx.coerceIn(2 * d, w - lw - 2 * d), sy + 5 * d, text)

        c.drawText("looking up", 6 * d, h - 6 * d, dimText)
        if (geoAlt < 0) {
            val note = "below horizon"
            c.drawText(note, w - dimText.measureText(note) - 6 * d, h - 6 * d, dimText)
        }
    }

    private fun drawPole(c: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
        val r = min(w * 0.42f, (h - 56 * d) / 2f).coerceAtLeast(20 * d)
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

/** One recorded observation. */
class Mark(
    val n: Int, val utcMs: Long, val target: String, val lat: Double, val lon: Double,
    val appAlt: Double, val geoAlt: Double, val az: Double, val ra: Double, val dec: Double
)

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
    private lateinit var coordLabel: TextView
    private lateinit var starInfo: TextView
    private lateinit var titleView: FitTextView
    private lateinit var prefs: SharedPreferences
    private lateinit var detector: GestureDetector
    private var starIndex = 0

    // ---- marks (recorded values) and voice control
    private val marks = ArrayList<Mark>()
    private lateinit var statusLabel: TextView
    private lateinit var logBtn: Button
    private lateinit var micBtn: Button
    private var recognizer: SpeechRecognizer? = null
    private var voiceOn = false
    private var lastMarkAt = 0L
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }
    private val restoreStatus = Runnable { statusLabel.text = idleStatus() }
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
            handler.postDelayed(this, 30)
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(dim)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
    }

    private fun big(template: String, scale: Float = 1f, weight: Float = 1f) = FitTextView(this, template, scale).apply {
        setTextColor(red)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, weight
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
        altView = big(DMS_TEMPLATE, 0.9f, 0.7f)
        azLabel = label("AZIMUTH (from north)")
        azView = big(DMS_TEMPLATE, 0.9f, 0.7f)
        info = TextView(this).apply {
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
        }

        titleView = FitTextView(this, "", 0.9f).apply {
            setTextColor(red)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f)
        }
        coordLabel = label("").apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        starInfo = label("").apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Tap the star name to show or hide the latitude / longitude boxes.
        titleView.setOnClickListener {
            val show = row.visibility != View.VISIBLE
            row.visibility = if (show) View.VISIBLE else View.GONE
            coordLabel.visibility = if (show) View.GONE else View.VISIBLE
            if (!show) {
                currentFocus?.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(root.windowToken, 0)
            }
        }
        statusLabel = label("").apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val markBtn = redButton("MARK").apply { setOnClickListener { markNow("button") } }
        logBtn = redButton("LOG (0)").apply { setOnClickListener { showLog() } }
        micBtn = redButton("MIC OFF").apply { setOnClickListener { toggleVoice() } }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(markBtn); addView(logBtn); addView(micBtn)
        }
        root.addView(titleView); root.addView(starInfo); root.addView(coordLabel); root.addView(row)
        root.addView(altLabel); root.addView(altView)
        timeLabel = label("TIME (UTC)")
        timeView = big("88:88:88.88", 0.65f, 0.6f)
        sky = SkyView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.6f)
        }
        root.addView(azLabel); root.addView(azView)
        root.addView(sky)
        root.addView(timeLabel); root.addView(timeView)
        root.addView(statusLabel); root.addView(buttonRow)
        root.addView(info)
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

        prefs = getSharedPreferences("polaris", MODE_PRIVATE)
        loadMarks()
        refreshButtons()
        statusLabel.text = idleStatus()
        latIn.setText(prefs.getString("lat", ""))
        lonIn.setText(prefs.getString("lon", ""))
        val hasCoords = latIn.text.toString().toDoubleOrNull() != null &&
                lonIn.text.toString().toDoubleOrNull() != null
        row.visibility = if (hasCoords) View.GONE else View.VISIBLE
        coordLabel.visibility = if (hasCoords) View.VISIBLE else View.GONE

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

        // Swipe left / right anywhere on the screen to change star.
        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val minDist = 80 * resources.displayMetrics.density
                if (abs(dx) > minDist && abs(dx) > 1.5f * abs(dy) && abs(vx) > 300f) {
                    selectStar(starIndex + if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        selectStar(prefs.getInt("star", 0))
    }

    // =====================  MARK: record the current values  =====================

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun redButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(red)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(0, 0, 0, 0)
        background = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(dp(2), red)
            cornerRadius = dp(8).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
    }

    private fun utcString(ms: Long) = isoFmt.format(Date(ms)) + String.format(Locale.US, ".%02dZ", (ms % 1000) / 10)

    private fun idleStatus() =
        if (voiceOn) "mic on: say \"mark\"   (also MARK or volume-down)"
        else "mic off   (MARK button or volume-down also marks)"

    private fun setStatus(text: String) {
        statusLabel.text = text
        handler.removeCallbacks(restoreStatus)
        handler.postDelayed(restoreStatus, 3500)
    }

    private fun refreshButtons() {
        logBtn.text = "LOG (${marks.size})"
        micBtn.text = if (voiceOn) "MIC ON" else "MIC OFF"
    }

    /** Record exactly what the screen is showing right now. */
    private fun markNow(source: String) {
        val lat = latIn.text.toString().toDoubleOrNull()
        val lon = lonIn.text.toString().toDoubleOrNull()
        if (lat == null || lon == null || abs(lat) > 90 || abs(lon) > 180) {
            setStatus("cannot mark: enter latitude and longitude first")
            return
        }
        val now = System.currentTimeMillis()
        val target = TARGETS[starIndex]
        val r = Astro.compute(target, lat, lon, now)
        val mk = Mark(marks.size + 1, now, target.name, lat, lon, r.appAlt, r.geoAlt, r.az, r.ra, r.dec)
        marks.add(mk)
        saveMarks()
        refreshButtons()
        beepAndBuzz()
        setStatus(String.format(Locale.US, "MARKED #%d  %s  %s  (%s)", mk.n, target.name,
            bigClock.format(Date(now)) + String.format(Locale.US, ".%02d", (now % 1000) / 10), source))
    }

    @Suppress("DEPRECATION")
    private fun beepAndBuzz() {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120) } catch (e: RuntimeException) { }
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(80)
    }

    private fun csvLine(m: Mark) = String.format(
        Locale.US, "%d,%s,%d,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
        m.n, utcString(m.utcMs), m.utcMs, m.target, m.lat, m.lon, m.appAlt, m.geoAlt, m.az, m.ra, m.dec
    )

    private fun saveMarks() {
        prefs.edit().putString("marks", marks.joinToString("\n") { csvLine(it) }).apply()
    }

    private fun loadMarks() {
        marks.clear()
        for (line in (prefs.getString("marks", "") ?: "").lines()) {
            if (line.isBlank()) continue
            try {
                val f = line.split(",")
                marks.add(Mark(f[0].toInt(), f[2].toLong(), f[3], f[4].toDouble(), f[5].toDouble(),
                    f[6].toDouble(), f[7].toDouble(), f[8].toDouble(), f[9].toDouble(), f[10].toDouble()))
            } catch (e: Exception) { }
        }
    }

    private fun showLog() {
        val text = if (marks.isEmpty()) "No marks yet.\n\nSay \"mark\", tap MARK, or press volume-down."
        else marks.reversed().joinToString("\n\n") { m ->
            "#${m.n}  ${isoFmt.format(Date(m.utcMs))}" + String.format(Locale.US, ".%02d UTC", (m.utcMs % 1000) / 10) +
                    "\n${m.target.uppercase()}\nalt ${Astro.dmsFixed(if (m.appAlt < 0) '-' else ' ', m.appAlt)}" +
                    "\naz  ${Astro.dmsFixed(' ', m.az)}"
        }
        val tv = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            this.text = text
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Marks (${marks.size})")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Share") { _, _ -> shareMarks() }
            .setNeutralButton("Clear") { _, _ -> confirmClear() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareMarks() {
        if (marks.isEmpty()) return
        val csv = "n,utc,epoch_ms,target,lat,lon,alt_apparent_deg,alt_geometric_deg,az_deg,ra_deg,dec_deg\n" +
                marks.joinToString("\n") { csvLine(it) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Star marks")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(send, "Share marks"))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Delete all ${marks.size} marks?")
            .setPositiveButton("Delete") { _, _ ->
                marks.clear(); saveMarks(); refreshButtons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Volume-down also marks: instant, no speech delay.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) markNow("volume key")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // =====================  Voice: say "mark"  =====================

    private val restartListening = Runnable { if (voiceOn) listen() }

    private fun toggleVoice() {
        if (voiceOn) {
            voiceOn = false
            handler.removeCallbacks(restartListening)
            recognizer?.cancel()
            refreshButtons()
            statusLabel.text = idleStatus()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("speech recognition is not available on this phone")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
            return
        }
        voiceOn = true
        refreshButtons()
        statusLabel.text = idleStatus()
        listen()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) toggleVoice()
            else setStatus("microphone permission denied")
        }
    }

    private fun heardMark(b: Bundle?): Boolean {
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        return list.any { phrase -> phrase.lowercase(Locale.US).split(Regex("[^a-z]+")).contains("mark") }
    }

    private fun voiceMark() {
        val now = System.currentTimeMillis()
        if (now - lastMarkAt < 1500) return          // ignore repeats of the same word
        lastMarkAt = now
        markNow("voice")
        recognizer?.cancel()                          // start a fresh phrase for the next "mark"
        handler.removeCallbacks(restartListening)
        handler.postDelayed(restartListening, 250)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (voiceOn && heardMark(partialResults)) voiceMark()
        }

        override fun onResults(results: Bundle?) {
            if (!voiceOn) return
            if (heardMark(results)) voiceMark()
            else {
                handler.removeCallbacks(restartListening)
                handler.postDelayed(restartListening, 100)
            }
        }

        override fun onError(error: Int) {
            if (!voiceOn) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                voiceOn = false
                refreshButtons()
                setStatus("microphone permission missing")
                return
            }
            val delay = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> 1500L
                else -> 300L
            }
            handler.removeCallbacks(restartListening)
            handler.postDelayed(restartListening, delay)
        }
    }

    private fun listen() {
        if (!voiceOn) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        recognizer?.startListening(intent)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun selectStar(index: Int) {
        starIndex = ((index % TARGETS.size) + TARGETS.size) % TARGETS.size
        val tg = TARGETS[starIndex]
        titleView.text = tg.name.uppercase()
        starInfo.text = String.format(
            Locale.US, "\u2039  %d / %d  \u00B7  %s  \u203A", starIndex + 1, TARGETS.size, tg.sub
        )
        prefs.edit().putInt("star", starIndex).apply()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
        if (voiceOn) listen()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(restartListening)
        recognizer?.cancel()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun update() {
        val lat = latIn.text.toString().toDoubleOrNull()
        val lon = lonIn.text.toString().toDoubleOrNull()
        val now = System.currentTimeMillis()
        timeLabel.text = "TIME (UTC)  ${dateFmt.format(Date(now))}"
        coordLabel.text = if (lat != null && lon != null)
            "${latIn.text}, ${lonIn.text}   (tap name to edit)" else "tap name to set location"
        timeView.text = String.format(Locale.US, "%s.%02d", bigClock.format(Date(now)), (now % 1000) / 10)
        if (lat == null || lon == null || abs(lat) > 90 || abs(lon) > 180) {
            altView.text = "--"
            azView.text = "--"
            sky.clear()
            info.visibility = View.VISIBLE
            info.text = "Enter latitude (-90..90) and longitude (-180..180)\n${clock.format(Date(now))}"
            return
        }
        val target = TARGETS[starIndex]
        val isPolaris = target.name == "Polaris"
        val r = Astro.compute(target, lat, lon, now)
        val st = target.star
        val extra = when {
            st != null -> String.format(Locale.US, "mag %.2f", st.mag)
            target.body == 1 -> String.format(Locale.US, "%.4f AU", r.dist)
            target.body == 2 -> String.format(
                Locale.US, "%,d km  %d%% lit", (r.dist * 149597870.7).roundToLong(), (r.illum * 100).roundToInt()
            )
            else -> String.format(Locale.US, "%.3f AU", r.dist)
        }
        starInfo.text = String.format(
            Locale.US, "\u2039  %d / %d  \u00B7  %s  \u00B7  %s  \u203A",
            starIndex + 1, TARGETS.size, target.sub, extra
        )
        altLabel.text = if (r.appAlt > 0) "ALTITUDE (apparent)" else "ALTITUDE - BELOW HORIZON"
        altView.text = Astro.dmsFixed(if (r.appAlt < 0) '-' else ' ', r.appAlt)
        azLabel.text = (if (isPolaris) "AZIMUTH (E/W of north)  " else "AZIMUTH (from north)  ") +
                Astro.compass(r.az)
        azView.text = if (isPolaris) Astro.azOffset(r.az) else Astro.dmsFixed(' ', r.az)
        if (isPolaris) sky.showPole(r.az, r.geoAlt, lat, 90.0 - r.dec)
        else sky.showAllSky(target.name, r.az, r.geoAlt)
        info.visibility = View.GONE
    }
}
