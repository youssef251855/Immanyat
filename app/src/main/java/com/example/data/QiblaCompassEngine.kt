package com.example.data

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class CompassSensorStatus {
    DETECTING,
    ACTIVE_HIGH,
    ACTIVE_MEDIUM,
    ACTIVE_LOW,
    UNAVAILABLE
}

data class CompassState(
    val azimuthDegrees: Float = 0f,
    val continuousAzimuth: Float = 0f,
    val status: CompassSensorStatus = CompassSensorStatus.DETECTING,
    val sensorSource: String = "",
    val magneticDeclination: Float = 0f
)

object CompassMath {
    fun normalizeAngle360(deg: Float): Float {
        if (deg.isNaN() || deg.isInfinite()) return 0f
        var normalized = deg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    /**
     * Returns shortest signed angular difference in (-180, 180] from [fromDeg] to [toDeg].
     */
    fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var diff = (normalizeAngle360(toDeg) - normalizeAngle360(fromDeg) + 540f) % 360f - 180f
        if (diff <= -180f) diff += 360f
        return diff
    }

    /**
     * Unwraps [nextTarget360] relative to [previousContinuous] so crossing 0°/360°
     * animates cleanly by +2° instead of spinning backwards by -358°.
     */
    fun unwrapAngle(previousContinuous: Float, nextTarget360: Float): Float {
        val delta = shortestAngleDelta(previousContinuous, nextTarget360)
        return previousContinuous + delta
    }

    /**
     * Exponential moving average on the unit circle (safe across 0°/360° boundary).
     */
    fun circularLowPassFilter(previousDeg: Float?, newDeg: Float, alpha: Float = 0.22f): Float {
        val cleanNew = normalizeAngle360(newDeg)
        if (previousDeg == null) return cleanNew
        val delta = shortestAngleDelta(previousDeg, cleanNew)
        return normalizeAngle360(previousDeg + alpha * delta)
    }

    fun computeMagneticDeclination(lat: Double, lng: Double): Float {
        return try {
            val field = GeomagneticField(
                lat.toFloat(),
                lng.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            field.declination
        } catch (_: Throwable) {
            0f
        }
    }
}

@Composable
fun rememberCompassState(lat: Double, lng: Double): CompassState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val declination = remember(lat, lng) {
        CompassMath.computeMagneticDeclination(lat, lng)
    }

    var filteredAzimuth by remember { mutableStateOf<Float?>(null) }
    var continuousAzimuth by remember { mutableFloatStateOf(0f) }
    var sensorStatus by remember { mutableStateOf(CompassSensorStatus.DETECTING) }
    var sensorSource by remember { mutableStateOf("") }
    var isResumed by remember { mutableStateOf(true) }
    var fallbackTriggered by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // If primary TYPE_ROTATION_VECTOR exists on an OEM/emulator but yields zero events within 1.5s,
    // automatically switch to GEOMAGNETIC_ROTATION_VECTOR + ACCEL/MAGNETIC_FIELD, or mark UNAVAILABLE.
    LaunchedEffect(isResumed, fallbackTriggered) {
        if (!isResumed) return@LaunchedEffect
        if (sensorStatus == CompassSensorStatus.DETECTING && !fallbackTriggered) {
            delay(1500)
            if (filteredAzimuth == null) {
                fallbackTriggered = true
            }
        } else if (sensorStatus == CompassSensorStatus.DETECTING && fallbackTriggered) {
            delay(1800)
            if (filteredAzimuth == null) {
                sensorStatus = CompassSensorStatus.UNAVAILABLE
            }
        }
    }

    DisposableEffect(isResumed, fallbackTriggered, declination) {
        if (!isResumed) {
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            sensorStatus = CompassSensorStatus.UNAVAILABLE
            return@DisposableEffect onDispose { }
        }

        val rotationSensor = if (!fallbackTriggered) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        } else {
            null
        }
        val geomagneticRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor == null && geomagneticRotationSensor == null && (accelSensor == null || magnetSensor == null)) {
            sensorStatus = CompassSensorStatus.UNAVAILABLE
            return@DisposableEffect onDispose { }
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var activeAccuracyStatus = CompassSensorStatus.ACTIVE_HIGH

        fun updateAzimuthFromMatrix(rotationMatrix: FloatArray, sourceLabel: String) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // If user holds the phone upright/tilted (>55 deg pitch), remap axes so heading doesn't freeze/flip
            val pitchDeg = abs(Math.toDegrees(orientation[1].toDouble()))
            val effectiveAzimuthRad = if (pitchDeg > 55.0) {
                val remapped = FloatArray(9)
                if (SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remapped
                    )
                ) {
                    val uprightOrientation = FloatArray(3)
                    SensorManager.getOrientation(remapped, uprightOrientation)
                    uprightOrientation[0]
                } else {
                    orientation[0]
                }
            } else {
                orientation[0]
            }

            val magneticAzimuth = (effectiveAzimuthRad * 180.0 / PI).toFloat()
            val trueNorthAzimuth = CompassMath.normalizeAngle360(magneticAzimuth + declination)

            val smoothed = CompassMath.circularLowPassFilter(filteredAzimuth, trueNorthAzimuth, alpha = 0.22f)
            filteredAzimuth = smoothed
            continuousAzimuth = CompassMath.unwrapAngle(continuousAzimuth, smoothed)
            sensorSource = sourceLabel
            sensorStatus = activeAccuracyStatus
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val accelValues = FloatArray(3)
            private val magnetValues = FloatArray(3)
            private var hasAccel = false
            private var hasMagnet = false
            private val accelAlpha = 0.18f

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val safeLen = minOf(event.values.size, 4)
                        if (safeLen < 3) return
                        val safeVec = FloatArray(safeLen) { i -> event.values[i] }
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, safeVec)
                        updateAzimuthFromMatrix(rotationMatrix, "Rotation Vector")
                    }
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        val safeLen = minOf(event.values.size, 4)
                        if (safeLen < 3) return
                        val safeVec = FloatArray(safeLen) { i -> event.values[i] }
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, safeVec)
                        updateAzimuthFromMatrix(rotationMatrix, "Geomagnetic Vector")
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (event.values.size < 3) return
                        if (!hasAccel) {
                            System.arraycopy(event.values, 0, accelValues, 0, 3)
                            hasAccel = true
                        } else {
                            for (i in 0..2) {
                                accelValues[i] = accelValues[i] + accelAlpha * (event.values[i] - accelValues[i])
                            }
                        }
                        if (hasMagnet && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)) {
                            updateAzimuthFromMatrix(rotationMatrix, "Accel + Magnetometer")
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        if (event.values.size < 3) return
                        if (!hasMagnet) {
                            System.arraycopy(event.values, 0, magnetValues, 0, 3)
                            hasMagnet = true
                        } else {
                            for (i in 0..2) {
                                magnetValues[i] = magnetValues[i] + accelAlpha * (event.values[i] - magnetValues[i])
                            }
                        }
                        if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)) {
                            updateAzimuthFromMatrix(rotationMatrix, "Accel + Magnetometer")
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
                    sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                ) {
                    activeAccuracyStatus = when (accuracy) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassSensorStatus.ACTIVE_HIGH
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassSensorStatus.ACTIVE_MEDIUM
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassSensorStatus.ACTIVE_LOW
                        else -> activeAccuracyStatus
                    }
                    if (filteredAzimuth != null) {
                        sensorStatus = activeAccuracyStatus
                    }
                }
            }
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
        } else {
            if (geomagneticRotationSensor != null) {
                sensorManager.registerListener(listener, geomagneticRotationSensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
            }
            if (accelSensor != null && magnetSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
                sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return CompassState(
        azimuthDegrees = filteredAzimuth ?: 0f,
        continuousAzimuth = continuousAzimuth,
        status = sensorStatus,
        sensorSource = sensorSource,
        magneticDeclination = declination
    )
}
