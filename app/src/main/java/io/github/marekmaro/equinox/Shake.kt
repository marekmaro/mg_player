package io.github.marekmaro.equinox

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Rozpoznawanie potrząśnięcia – czysta logika, bez Androida (łatwa do sprawdzenia testem).
 *
 * Potrząśnięcie = co najmniej [hits] mocnych ruchów (przyspieszenie ponad [thresholdG] g)
 * w ciągu [windowMs]. Odczyty bliżej niż [minGapMs] to ten sam ruch (czujnik podaje
 * kilkadziesiąt odczytów na sekundę, a jedno szarpnięcie trwa kilka z nich). Pojedyncze stuknięcie albo przewrócenie się na drugi bok w łóżku
 * tego nie spełnia. Po rozpoznaniu przez [cooldownMs] nic nie liczymy, żeby jedno
 * potrząśnięcie nie dodało czasu dwa razy.
 */
class ShakeLogic(
    private val thresholdG: Float = 2.3f,
    private val hits: Int = 3,
    private val windowMs: Long = 900,
    private val cooldownMs: Long = 2_000,
    private val minGapMs: Long = 120,
) {
    private val times = ArrayDeque<Long>()
    private var lastShake = Long.MIN_VALUE / 2

    /** Przyspieszenie w m/s² (x, y, z), czas w ms; true = właśnie rozpoznano potrząśnięcie. */
    fun onSample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (nowMs - lastShake < cooldownMs) return false
        val g = sqrt(x * x + y * y + z * z) / EARTH_G
        if (g < thresholdG) return false
        if (times.isNotEmpty() && nowMs - times.last() < minGapMs) return false
        times.addLast(nowMs)
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
        if (times.size >= hits) {
            times.clear()
            lastShake = nowMs
            return true
        }
        return false
    }

    private companion object {
        const val EARTH_G = 9.80665f
    }
}

/**
 * Nasłuch akcelerometru – włączany tylko na ostatnią minutę timera snu,
 * więc nie zużywa baterii przez całą noc. Telefony bez akcelerometru: nic się nie dzieje.
 */
class ShakeListener(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val logic = ShakeLogic()
    var active = false
        private set

    val available: Boolean get() = accel != null

    fun start() {
        if (active || accel == null) return
        active = sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (!active) return
        sensors?.unregisterListener(this)
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size >= 3 && logic.onSample(v[0], v[1], v[2], SystemClock.elapsedRealtime())) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
