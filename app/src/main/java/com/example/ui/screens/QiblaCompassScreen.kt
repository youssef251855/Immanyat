package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.example.data.AdhanManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EmaniatViewModel
import java.util.Locale
import kotlin.math.*

data class CityPreset(
    val name: String,
    val country: String,
    val lat: Double,
    val lng: Double
)

val PRESET_CITIES = listOf(
    CityPreset("مكة المكرمة", "المملكة العربية السعودية", 21.4225, 39.8262),
    CityPreset("المدينة المنورة", "المملكة العربية السعودية", 24.4672, 39.6111),
    CityPreset("الرياض", "المملكة العربية السعودية", 24.7136, 46.6753),
    CityPreset("القاهرة", "مصر", 30.0444, 31.2357),
    CityPreset("القدس الشريف", "فلسطين", 31.7683, 35.2137),
    CityPreset("دبي", "الإمارات", 25.2048, 55.2708),
    CityPreset("عمّان", "الأردن", 31.9454, 35.9284),
    CityPreset("بغداد", "العراق", 33.3152, 44.3661),
    CityPreset("دمشق", "سوريا", 33.5138, 36.2765),
    CityPreset("بيروت", "لبنان", 33.8938, 35.5018),
    CityPreset("الكويت", "الكويت", 29.3759, 47.9774),
    CityPreset("الدوحة", "قطر", 25.2854, 51.5310),
    CityPreset("المنامة", "البحرين", 26.2285, 50.5860),
    CityPreset("مسقط", "عُمان", 23.5880, 58.3829),
    CityPreset("الخرطوم", "السودان", 15.5007, 32.5599),
    CityPreset("الرباط", "المغرب", 34.0209, -6.8416),
    CityPreset("الجزائر", "الجزائر", 36.7538, 3.0588),
    CityPreset("تونس", "تونس", 36.8065, 10.1815),
    CityPreset("طرابلس", "ليبيا", 32.8872, 13.1913),
    CityPreset("إسطنبول", "تركيا", 41.0082, 28.9784),
    CityPreset("لندن", "المملكة المتحدة", 51.5074, -0.1278),
    CityPreset("باريس", "فرنسا", 48.8566, 2.3522),
    CityPreset("نيويورك", "الولايات المتحدة", 40.7128, -74.0060)
)

@Composable
fun QiblaCompassScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler {
        viewModel.setScreen(AppScreen.HOME)
        viewModel.triggerHaptic()
    }

    val savedCoords = remember { AdhanManager.getSavedCoordinates(context) }
    val savedLocationName = remember { AdhanManager.getSavedLocationName(context) }
    var selectedCity by remember {
        mutableStateOf(PRESET_CITIES.find { it.name == savedLocationName } ?: PRESET_CITIES[3])
    }
    var showCityPicker by remember { mutableStateOf(false) }

    var userLat by remember { mutableDoubleStateOf(savedCoords.latitude) }
    var userLng by remember { mutableDoubleStateOf(savedCoords.longitude) }
    var locationName by remember { mutableStateOf(savedLocationName) }

    // Compass Sensor Azimuth state (0 to 360)
    var deviceAzimuth by remember { mutableFloatStateOf(0f) }
    var hasCompassSensor by remember { mutableStateOf(true) }

    // Smooth needle animation
    val animatedAzimuth by animateFloatAsState(
        targetValue = deviceAzimuth,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "azimuthAnim"
    )

    // Calculate Bearing to Kaaba (21.4225, 39.8262)
    val kaabaLat = 21.422487
    val kaabaLng = 39.826206

    val qiblaBearing = remember(userLat, userLng) {
        calculateQiblaBearing(userLat, userLng, kaabaLat, kaabaLng)
    }

    val distanceToKaabaKm = remember(userLat, userLng) {
        calculateDistanceKm(userLat, userLng, kaabaLat, kaabaLng)
    }

    // Relative angle between device heading and Kaaba
    val relativeAngle = (qiblaBearing - animatedAzimuth + 360f) % 360f
    val isAligned = relativeAngle in 356.5f..360f || relativeAngle in 0f..3.5f

    // Haptic feedback upon perfect alignment
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    var lastVibratedAligned by remember { mutableStateOf(false) }

    LaunchedEffect(isAligned) {
        if (isAligned && !lastVibratedAligned && viewModel.isVibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            } catch (t: Throwable) {}
        }
        lastVibratedAligned = isAligned
    }

    // Register Sensor Listener
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = if (rotationSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val magnetSensor = if (rotationSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        if (rotationSensor == null && (accelSensor == null || magnetSensor == null)) {
            hasCompassSensor = false
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)
            private val accelValues = FloatArray(3)
            private val magnetValues = FloatArray(3)
            private var hasAccel = false
            private var hasMagnet = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                        if (azimuthDeg < 0) azimuthDeg += 360f
                        deviceAzimuth = azimuthDeg
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelValues, 0, 3)
                        hasAccel = true
                        if (hasMagnet && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)) {
                            SensorManager.getOrientation(rotationMatrix, orientationValues)
                            var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                            if (azimuthDeg < 0) azimuthDeg += 360f
                            deviceAzimuth = azimuthDeg
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetValues, 0, 3)
                        hasMagnet = true
                        if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)) {
                            SensorManager.getOrientation(rotationMatrix, orientationValues)
                            var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                            if (azimuthDeg < 0) azimuthDeg += 360f
                            deviceAzimuth = azimuthDeg
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelSensor != null && magnetSensor != null) {
            sensorManager?.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // Try getting last known location only when runtime permission is granted
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            getLastKnownLocationSafely(context, fineGranted)?.let { lastLoc ->
                userLat = lastLoc.latitude
                userLng = lastLoc.longitude
                locationName = "موقعي الحالي (GPS)"
            }
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("qibla_compass_screen")
    ) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.setScreen(AppScreen.HOME)
                        viewModel.triggerHaptic()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "الرجوع",
                        tint = GoldAccent
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "بوصلة القبلة المشرفة 🕋",
                        color = GoldAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تحديد دقيق باتجاه الكعبة المعظمة",
                        color = TextColorSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { showCityPicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "تغيير المدينة",
                        tint = GoldAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Location & Direction Pill
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCityPicker = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(EmeraldMuted.copy(alpha = 0.25f), CircleShape)
                                .border(1.dp, GoldAccent.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = locationName,
                                color = LightWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "المسافة للكعبة: ${String.format(Locale.US, "%,.0f", distanceToKaabaKm)} كم",
                                color = EmeraldSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(GoldAccent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${qiblaBearing.toInt()}° زاوية القبلة",
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alignment Status Glow Pill
            AnimatedContent(
                targetState = isAligned,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "alignmentGlow"
            ) { aligned ->
                if (aligned) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF1B5E20))
                                )
                            )
                            .border(1.5.dp, GoldAccent, RoundedCornerShape(14.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "✨", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "أنت الآن في اتجاه القبلة تماماً 🕋",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "✨", fontSize = 18.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EmeraldMuted.copy(alpha = 0.15f))
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val diff = ((qiblaBearing - animatedAzimuth + 360) % 360).toInt()
                        val turnAdvice = if (diff > 180) "أدر الهاتف نحو اليسار بمقدار ${360 - diff}°" else "أدر الهاتف نحو اليمين بمقدار $diff°"
                        Text(
                            text = "🔄 $turnAdvice للوصول لاتجاه الكعبة",
                            color = TextColorSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BIG COMPASS ROSE & KAABA POINTER
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(if (isAligned) 20.dp else 10.dp, CircleShape, spotColor = if (isAligned) GoldAccent else Color.Black)
                    .background(SurfaceDarkGlass, CircleShape)
                    .border(
                        BorderStroke(
                            width = if (isAligned) 3.dp else 1.5.dp,
                            color = if (isAligned) GoldAccent else CardBorder
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Dial Canvas rotating with device Azimuth
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Draw Dial relative to device heading
                    rotate(degrees = -animatedAzimuth, pivot = center) {
                        // Degree ticks
                        for (angle in 0 until 360 step 15) {
                            val isMajor = angle % 45 == 0
                            val tickLen = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
                            val strokeW = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                            val color = when (angle) {
                                0 -> Color(0xFFE53935) // North is Red
                                else -> if (isMajor) GoldAccent.copy(alpha = 0.7f) else TextColorSecondary.copy(alpha = 0.3f)
                            }

                            val rad = Math.toRadians(angle.toDouble())
                            val startX = center.x + (radius - tickLen) * sin(rad).toFloat()
                            val startY = center.y - (radius - tickLen) * cos(rad).toFloat()
                            val endX = center.x + radius * sin(rad).toFloat()
                            val endY = center.y - radius * cos(rad).toFloat()

                            drawLine(
                                color = color,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                        }

                        // Qibla Target Point marker on outer rim
                        val qiblaRad = Math.toRadians(qiblaBearing.toDouble())
                        val qiblaX = center.x + (radius - 4.dp.toPx()) * sin(qiblaRad).toFloat()
                        val qiblaY = center.y - (radius - 4.dp.toPx()) * cos(qiblaRad).toFloat()
                        drawCircle(
                            color = GoldAccent,
                            radius = 6.dp.toPx(),
                            center = Offset(qiblaX, qiblaY)
                        )
                    }

                    // Outer green alignment circle ring
                    drawCircle(
                        color = if (isAligned) GoldAccent.copy(alpha = 0.2f) else Color.Transparent,
                        radius = radius - 20.dp.toPx(),
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                // Kaaba Needle pointing directly towards Mecca
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(relativeAngle),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 24.dp)
                    ) {
                        // Golden Kaaba Symbol pointing north to Kaaba
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(8.dp, RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .border(1.5.dp, GoldAccent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(GoldAccent)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🕋",
                                    fontSize = 18.sp
                                )
                            }
                        }

                        // Needle line
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(50.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(GoldAccent, GoldAccent.copy(alpha = 0.2f), Color.Transparent)
                                    )
                                )
                        )

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Center Compass Hub
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(6.dp, CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(DarkBackgroundElevated, DarkBackground)
                            ),
                            CircleShape
                        )
                        .border(1.dp, GoldAccent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${animatedAzimuth.toInt()}°",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Calibration & Tips Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "نصائح المعايرة",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نصيحة لمعايرة البوصلة:",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "حرّك الهاتف على شكل رقم (8) في الهواء بعيداً عن المعادن والأجهزة الإلكترونية لضبط حساسية البوصلة.",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch City Button
            OutlinedButton(
                onClick = { showCityPicker = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "اختيار دولة أو مدينة أخرى من القائمة (${PRESET_CITIES.size} مدينة)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        // City Picker Bottom Sheet / Dialog
        if (showCityPicker) {
            AlertDialog(
                onDismissRequest = { showCityPicker = false },
                containerColor = DarkBackgroundElevated,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "اختر مدينتك لحساب القبلة 🌍",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        PRESET_CITIES.forEach { city ->
                            val isCurrent = city.name == locationName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCurrent) EmeraldMuted.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedCity = city
                                        userLat = city.lat
                                        userLng = city.lng
                                        locationName = "${city.name} (${city.country})"
                                        showCityPicker = false
                                        viewModel.triggerHaptic()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = city.name,
                                        color = if (isCurrent) GoldAccent else LightWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = city.country,
                                        color = TextColorSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCityPicker = false }) {
                        Text(text = "إغلاق", color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Great Circle Navigation formula for Kaaba Azimuth
fun calculateQiblaBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    var bearing = Math.toDegrees(atan2(y, x))
    if (bearing < 0) bearing += 360.0
    return bearing.toFloat()
}

// Distance Calculation to Mecca
fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Radius of earth in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocationSafely(context: Context, fineGranted: Boolean): Location? {
    return try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val gpsLoc = if (fineGranted) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        gpsLoc ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (_: Throwable) {
        null
    }
}

