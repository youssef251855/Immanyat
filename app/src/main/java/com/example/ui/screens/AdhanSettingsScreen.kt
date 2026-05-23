package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AdhanManager
import com.example.data.MosqueModel
import com.example.data.PrayerTimeInfo
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdhanSettingsScreen(
    viewModel: EmaniatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) } // 0: الأوقات والمؤذنين, 1: القبلة والمساجد, 2: الإعدادات والأذكار
    var locationName by remember { mutableStateOf(AdhanManager.getSavedLocationName(context)) }
    var prayerTimesList by remember { mutableStateOf(AdhanManager.calculatePrayerTimesForDate(context, Date())) }
    
    // Auto-update times & countdowns
    LaunchedEffect(Unit) {
        while (true) {
            prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
            delay(10000)
        }
    }

    // GPS location fetcher callback
    val locContext = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.createAttributionContext("Location")
        } else {
            context
        }
    }
    val fusedLocationClient = remember(locContext) { LocationServices.getFusedLocationProviderClient(locContext) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        scope.launch {
                            var city = "موقعي الحالي"
                            try {
                                val geocoder = Geocoder(context, Locale("ar"))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { addresses ->
                                        if (addresses.isNotEmpty()) {
                                            city = addresses[0].locality ?: addresses[0].adminArea ?: "موقعي الحالي"
                                            AdhanManager.saveCoordinates(context, loc.latitude, loc.longitude, city)
                                            locationName = city
                                            
                                            // Ensure calculating times and scheduling runs in UI or background appropriately
                                            scope.launch(Dispatchers.Main) {
                                                prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
                                                AdhanManager.scheduleNextAlarm(context)
                                            }
                                        }
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    val list = withContext(Dispatchers.IO) {
                                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                    }
                                    if (!list.isNullOrEmpty()) {
                                        city = list[0].locality ?: list[0].adminArea ?: "موقعي الحالي"
                                    }
                                    AdhanManager.saveCoordinates(context, loc.latitude, loc.longitude, city)
                                    locationName = city
                                    prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
                                    AdhanManager.scheduleNextAlarm(context)
                                }
                            } catch (e: Exception) {
                                AdhanManager.saveCoordinates(context, loc.latitude, loc.longitude, "الموقع الجغرافي")
                                locationName = "الموقع الجغرافي"
                                prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
                                AdhanManager.scheduleNextAlarm(context)
                            }
                            Toast.makeText(context, "تم تحديث الموقع وتوقيت الصلاة تلقائيًا ✨", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "لم نتمكن من التقاط إحداثيات GPS المعاصرة عِوضًا عن شبكتك.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: SecurityException) {
                Toast.makeText(context, "صلاحية الموقع مرفوضة من قبل نظام الهاتف.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "يجب السماح بالوصول للموقع لتحديد التواقيت المحلية.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Decorative design patterns
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { 
                        viewModel.triggerHaptic()
                        onClose() 
                    },
                    modifier = Modifier
                        .background(SurfaceDarkGlass, CircleShape)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = GoldAccent
                    )
                }

                Text(
                    text = "نظام الأذان والقبلة المطور 🕌",
                    color = GoldAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // GPS sync button
                IconButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                            // Fetch directly
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        AdhanManager.saveCoordinates(context, loc.latitude, loc.longitude, "تحديد تلقائي")
                                        prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
                                        AdhanManager.scheduleNextAlarm(context)
                                        Toast.makeText(context, "تم مزامنة إحداثيات موقعك الجغرافي بنجاح!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: SecurityException) {}
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .background(SurfaceDarkGlass, CircleShape)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "GPS مزامنة",
                        tint = EmeraldSecondary
                    )
                }
            }

            // Tabs Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val menuItems = listOf("مواقيت وأذان 🔊", "القبلة والمساجد 🧭", "الضبط الذكي ⚙️")
                menuItems.forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) EmeraldMuted.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable {
                                viewModel.triggerHaptic()
                                selectedTab = idx
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) GoldAccent else TextColorSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Screen Content Scrollable container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (selectedTab) {
                    0 -> TimesAndAudioTab(
                        context = context,
                        viewModel = viewModel,
                        prayerTimesList = prayerTimesList,
                        locationName = locationName,
                        onRefresh = { prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date()) }
                    )
                    1 -> CompassAndMosquesTab(context = context, viewModel = viewModel)
                    2 -> SmartSettingsTab(context = context, viewModel = viewModel, onRefresh = {
                        prayerTimesList = AdhanManager.calculatePrayerTimesForDate(context, Date())
                    })
                }
            }
        }
    }
}

// ============================================ TAB 0: TIMES & AUDIOS ============================================
@Composable
fun TimesAndAudioTab(
    context: Context,
    viewModel: EmaniatViewModel,
    prayerTimesList: List<PrayerTimeInfo>,
    locationName: String,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var downloadProgressMap = remember { mutableStateMapOf<String, Float>() }
    var downloadJobMap = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active Location Pill Info
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "المدينة",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "موقع الصلاة النشط",
                                color = TextColorSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = locationName,
                                color = LightWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(EmeraldSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, GoldAccent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val coords = AdhanManager.getSavedCoordinates(context)
                        Text(
                            text = String.format(Locale.US, "%.3f°, %.3f°", coords.latitude, coords.longitude),
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Beautiful List of 5 prayers (plus sunrise if displayed but sunrise is invalid for athan play)
        items(prayerTimesList) { prayer ->
            val prayerKey = prayer.name
            val arName = prayer.arabicName
            var isEnabled by remember { mutableStateOf(prayer.isEnabled) }
            var activeMuadhin by remember { mutableStateOf(AdhanManager.getMuadhinForPrayer(context, prayerKey)) }
            var manualOffset by remember { mutableStateOf(AdhanManager.getManualOffset(context, prayerKey)) }
            var isMenuExpanded by remember { mutableStateOf(false) }

            val isDownloaded = AdhanManager.isAthanDownloaded(context, activeMuadhin)
            val isDownloading = downloadJobMap[activeMuadhin] ?: false
            val progress = downloadProgressMap[activeMuadhin] ?: 0f

            // Check if this prayer is current or next highlights
            val isSunrise = prayerKey == "Sunrise"

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isEnabled && !isSunrise) GoldAccent.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isSunrise) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { chk ->
                                        viewModel.triggerHaptic()
                                        isEnabled = chk
                                        AdhanManager.setPrayerEnabled(context, prayerKey, chk)
                                        AdhanManager.scheduleNextAlarm(context)
                                        onRefresh()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldSecondary,
                                        uncheckedColor = TextColorSecondary
                                    )
                                )
                            } else {
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                            
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = arName,
                                        color = if (isEnabled) LightWhite else TextColorSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSunrise) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(GoldMuted.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("لا أذان لها", color = GoldAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = prayer.formattedTime,
                                    color = if (isEnabled) GoldAccent else TextColorSecondary.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Right side: Muadhin selector & Audio downloading
                        if (!isSunrise && isEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Muadhin text picker button
                                Box {
                                    Button(
                                        onClick = { isMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkGlass),
                                        border = BorderStroke(0.5.dp, CardBorder),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = activeMuadhin,
                                            color = GoldAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "قائمة",
                                            tint = GoldAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isMenuExpanded,
                                        onDismissRequest = { isMenuExpanded = false },
                                        modifier = Modifier.background(SurfaceDarkGlass)
                                    ) {
                                        AdhanManager.MUADHIN_URLS.keys.forEach { muadhinName ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = muadhinName, 
                                                        color = LightWhite, 
                                                        fontWeight = if (activeMuadhin == muadhinName) FontWeight.Bold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    viewModel.triggerHaptic()
                                                    activeMuadhin = muadhinName
                                                    AdhanManager.setMuadhinForPrayer(context, prayerKey, muadhinName)
                                                    AdhanManager.scheduleNextAlarm(context)
                                                    isMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Download status indicator / offline cacher trigger
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(24.dp),
                                        color = EmeraldSecondary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            viewModel.triggerHaptic()
                                            if (isDownloaded) {
                                                // delete to clear space
                                                AdhanManager.deleteAthan(context, activeMuadhin)
                                                Toast.makeText(context, "تم إزالة ملف صوت $activeMuadhin وتفعيل البث السحابي.", Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            } else {
                                                // download now
                                                scope.launch {
                                                    downloadJobMap[activeMuadhin] = true
                                                    val success = AdhanManager.downloadAthan(context, activeMuadhin) { prog ->
                                                        downloadProgressMap[activeMuadhin] = prog
                                                    }
                                                    downloadJobMap[activeMuadhin] = false
                                                    if (success) {
                                                        Toast.makeText(context, "تم تحميل أذان $activeMuadhin بالكامل للعمل دون إنترنت! 🎉", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "تحقق من اتصال الإنترنت وحاول مجددًا.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    onRefresh()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Default.OfflinePin else Icons.Outlined.CloudDownload,
                                            contentDescription = "تثبيت الملف",
                                            tint = if (isDownloaded) EmeraldSecondary else GoldAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Offset adjustment panels
                    if (!isSunrise && isEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDarkGlass.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الضبط اليدوي لدقائق التوقيت:",
                                color = TextColorSecondary,
                                fontSize = 11.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.triggerHaptic()
                                        manualOffset--
                                        AdhanManager.setManualOffset(context, prayerKey, manualOffset)
                                        AdhanManager.scheduleNextAlarm(context)
                                        onRefresh()
                                    },
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(SurfaceDarkGlass, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "تقليل دقيقة", tint = GoldAccent, modifier = Modifier.size(14.dp))
                                }

                                Text(
                                    text = if (manualOffset >= 0) "+$manualOffset د" else "$manualOffset د",
                                    color = if (manualOffset != 0) GoldAccent else LightWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.Center
                                )

                                IconButton(
                                    onClick = {
                                        viewModel.triggerHaptic()
                                        manualOffset++
                                        AdhanManager.setManualOffset(context, prayerKey, manualOffset)
                                        AdhanManager.scheduleNextAlarm(context)
                                        onRefresh()
                                    },
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(SurfaceDarkGlass, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "زيادة دقيقة", tint = GoldAccent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================ TAB 1: COMPASS & MOSQUES ============================================
@Composable
fun CompassAndMosquesTab(
    context: Context,
    viewModel: EmaniatViewModel
) {
    var deviceAzimuth by remember { mutableStateOf(0f) }
    val qiblaDegree = remember { AdhanManager.calculateQiblaDirection(context) }
    
    val mosqueList = remember { AdhanManager.getNearestMosques(context) }

    // Compass Sensor listener
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    
    // Fallback sensors if rotation vector is missing
    val accelSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magnetSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }

    DisposableEffect(Unit) {
        val sensorEventListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)
            
            private var lastAccelerometer = FloatArray(3)
            private var lastMagnetometer = FloatArray(3)
            private var accelSet = false
            private var magnetSet = false
            private var lastAzimuth = 0f

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                try {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        var azimuthRad = orientationValues[0]
                        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                        azimuthDeg = (azimuthDeg + 360f) % 360f
                        // Throttle state changes to prevent input dispatcher and main thread blockage
                        if (Math.abs(azimuthDeg - lastAzimuth) > 2.0f) {
                            lastAzimuth = azimuthDeg
                            deviceAzimuth = azimuthDeg
                        }
                    } else {
                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                            val length = minOf(event.values.size, lastAccelerometer.size)
                            System.arraycopy(event.values, 0, lastAccelerometer, 0, length)
                            accelSet = true
                        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                            val length = minOf(event.values.size, lastMagnetometer.size)
                            System.arraycopy(event.values, 0, lastMagnetometer, 0, length)
                            magnetSet = true
                        }
                        if (accelSet && magnetSet) {
                            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
                            SensorManager.getOrientation(rotationMatrix, orientationValues)
                            var azimuthRad = orientationValues[0]
                            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                            azimuthDeg = (azimuthDeg + 360f) % 360f
                            if (Math.abs(azimuthDeg - lastAzimuth) > 2.5f) {
                                lastAzimuth = azimuthDeg
                                deviceAzimuth = azimuthDeg
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdhanSettingsScreen", "Error calculation compass rotation", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            if (accelSensor != null) {
                sensorManager.registerListener(sensorEventListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (magnetSensor != null) {
                sensorManager.registerListener(sensorEventListener, magnetSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    // Dynamic angles
    val compassRotation = -deviceAzimuth
    val qiblaRotationInDial = (qiblaDegree - deviceAzimuth).toFloat()

    // check lock-on tolerance
    val isQiblaLocked = Math.abs(qiblaRotationInDial) < 3f || Math.abs(qiblaRotationInDial - 360f) < 3f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Live Qibla Compass representation ---
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                    text = "مؤشر بوصلة القبلة الذاتي 🕋",
                    color = GoldAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "ضع الهاتف مستويًا وافركه لتحديد أدق للزوايا المغناطيسية.",
                    color = TextColorSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(SurfaceDarkGlass)
                        .border(
                            2.dp, 
                            if (isQiblaLocked) EmeraldSecondary else CardBorder, 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // rotating dial representation
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(compassRotation)
                    ) {
                        // Dial markings (North, East, South, West)
                        Text("N", color = MutedRed, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopCenter).padding(10.dp))
                        Text("S", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
                        Text("E", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(10.dp))
                        Text("W", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterStart).padding(10.dp))
                    }

                    // Rotating Kaaba Target pointer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(qiblaRotationInDial),
                        contentAlignment = Alignment.Center
                    ) {
                        // Green Kaaba ray indicating the goal
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxHeight().padding(vertical = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignTop,
                                contentDescription = "مؤشر الكعبة",
                                tint = if (isQiblaLocked) EmeraldSecondary else GoldAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Centered Kaaba beautiful icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (isQiblaLocked) EmeraldSecondary.copy(alpha = 0.3f)
                                else CardBorder.copy(alpha = 0.4f)
                            )
                            .border(1.5.dp, if (isQiblaLocked) EmeraldSecondary else GoldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mosque,
                            contentDescription = "الكعبة المشرفة",
                            tint = if (isQiblaLocked) LightWhite else GoldAccent,
                            modifier = Modifier.size(32.dp).scale(if (isQiblaLocked) 1.15f else 1.0f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isQiblaLocked) "تم محاذاة وجهتك للقبلة تماماً! ✨" else "قم بتدوير الهاتف باتجاه السهم العلوي",
                        color = if (isQiblaLocked) EmeraldSecondary else LightWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "الانحراف المحسوب للقبلة: %.1f° من الشمال", qiblaDegree),
                        color = TextColorSecondary,
                        fontSize = 11.sp
                    )
                }
                }
            }
        }

        // --- Nearest Mosque List ---
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = "المساجد", tint = GoldAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المساجد القريبة بمحاذاتك 🏘️",
                        color = LightWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(mosqueList) { mosque ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldMuted.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mosque, contentDescription = "مسجد", tint = GoldAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = mosque.name, color = LightWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = mosque.address, color = TextColorSecondary, fontSize = 10.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${mosque.distanceMeters} متر", color = EmeraldSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "بزاوية %.0f°", mosque.directionDegrees),
                            color = TextColorSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================ TAB 2: SMART SETTINGS TAB ============================================
@Composable
fun SmartSettingsTab(
    context: Context,
    viewModel: EmaniatViewModel,
    onRefresh: () -> Unit
) {
    var volume by remember { mutableStateOf(AdhanManager.getAthanVolume(context)) }
    var isGradual by remember { mutableStateOf(AdhanManager.isGradualVolume(context)) }
    var isVibrate by remember { mutableStateOf(AdhanManager.isVibrateOnAdhan(context)) }
    var method by remember { mutableStateOf(AdhanManager.getCalculationMethod(context)) }
    var madhab by remember { mutableStateOf(AdhanManager.getMadhab(context)) }
    
    var preAlertMins by remember { mutableStateOf(AdhanManager.getPreAdhanMinutes(context)) }
    var silentSleep by remember { mutableStateOf(AdhanManager.isSilentDuringSleep(context)) }
    var travelMode by remember { mutableStateOf(AdhanManager.isTravelMode(context)) }
    var daylightSaving by remember { mutableStateOf(AdhanManager.isDaylightSaving(context)) }

    var azkarRemind by remember { mutableStateOf(AdhanManager.isPostPrayerAzkarReminderEnabled(context)) }
    var qiyamRemind by remember { mutableStateOf(AdhanManager.isQiyamReminderEnabled(context)) }
    var fastingRemind by remember { mutableStateOf(AdhanManager.isFastingReminderEnabled(context)) }

    val methodsRaw = listOf(
        "UMM_AL_QURA" to "أم القرى (مكة المكرمة)",
        "EGYPTIAN" to "الهيئة المصرية العامة للمساحة",
        "MWL" to "رابطة العالم الإسلامي",
        "ISNA" to "الجمعية الإسلامية لأمريكا الشمالية ISNA",
        "KARACHI" to "جامعة العلوم الإسلامية بكراتشي"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Calculation methods section
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "طرق الحساب والمذهب الفقهي ⚖️",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Methods row buttons
                Text(text = "طريقة الحساب المعتمدة:", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    methodsRaw.forEach { pair ->
                        val isSelected = method == pair.first
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EmeraldMuted.copy(alpha = 0.2f) else CardBorder.copy(alpha = 0.2f))
                                .clickable {
                                    viewModel.triggerHaptic()
                                    method = pair.first
                                    AdhanManager.setCalculationMethod(context, pair.first)
                                    AdhanManager.scheduleNextAlarm(context)
                                    onRefresh()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = pair.second, color = if (isSelected) GoldAccent else LightWhite, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "محدد", tint = EmeraldSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Madhab
                Text(text = "المذهب الفقهي لـ العصر:", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SHAFI" to "الجمهور (شافعي، مالكي، حنبلي)", "HANAFI" to "المذهب الحنفي").forEach { pair ->
                        val isSelected = madhab == pair.first
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EmeraldMuted.copy(alpha = 0.2f) else CardBorder.copy(alpha = 0.1f))
                                .border(0.5.dp, if (isSelected) GoldAccent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.triggerHaptic()
                                    madhab = pair.first
                                    AdhanManager.setMadhab(context, pair.first)
                                    AdhanManager.scheduleNextAlarm(context)
                                    onRefresh()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pair.second,
                                color = if (isSelected) GoldAccent else LightWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Sound volume configurations
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "مستوى صوت شعائر الأذان 🔊",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (volume < 0.1f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "مستوى الصوت",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = volume,
                        onValueChange = { vol ->
                            volume = vol
                            AdhanManager.setAthanVolume(context, vol)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = GoldAccent,
                            activeTrackColor = EmeraldSecondary,
                            inactiveTrackColor = CardBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        color = LightWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Gradual rise and vibration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "تدريجي", tint = EmeraldSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("رفع الصوت تدريجيًا", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("يرتفع الصوت الهادئ تدريجيًا لتجنب الفزع.", color = TextColorSecondary, fontSize = 9.sp)
                        }
                    }
                    Switch(
                        checked = isGradual,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            isGradual = chk
                            AdhanManager.setGradualVolume(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = "اهتزاز", tint = EmeraldSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("اهتزاز خفيف للنبض والإنذار", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تشغيل نبض اهتزاز خفيف مع إشارة الله أكبر الأولى.", color = TextColorSecondary, fontSize = 9.sp)
                        }
                    }
                    Switch(
                        checked = isVibrate,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            isVibrate = chk
                            AdhanManager.setVibrateOnAdhan(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }
            }
        }

        // Smart alert flags
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "الأوضاع والتنبيهات الذكية ⏰",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Pre Adhan alerts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("التنبيه المسبق للاستعداد", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("صفارة اهتزاز ناعمة تسبق إشارة الأذان.", color = TextColorSecondary, fontSize = 9.sp)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0 to "مغلق", 5 to "5د", 10 to "10د", 15 to "15د").forEach { pair ->
                            val isSelected = preAlertMins == pair.first
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) EmeraldSecondary else CardBorder.copy(alpha = 0.2f))
                                    .clickable {
                                        viewModel.triggerHaptic()
                                        preAlertMins = pair.first
                                        AdhanManager.setPreAdhanMinutes(context, pair.first)
                                        AdhanManager.scheduleNextAlarm(context)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = pair.second, color = if (isSelected) LightWhite else TextColorSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Silent sleep settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bedtime, contentDescription = "وضع النوم", tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("الوضع الصامت أثناء النوم", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("كتم الأذان الصوتي تلقائيًا بين (11م - 5ص).", color = TextColorSecondary, fontSize = 9.sp)
                        }
                    }
                    Switch(
                        checked = silentSleep,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            silentSleep = chk
                            AdhanManager.setSilentDuringSleep(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Travel Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlightTakeoff, contentDescription = "وضع السفر", tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("وضع السفر والرحلات", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تعطيل التنبيهات ريثما تصل وتستقر إحداثيات موقعك الجديد.", color = TextColorSecondary, fontSize = 9.sp)
                        }
                    }
                    Switch(
                        checked = travelMode,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            travelMode = chk
                            AdhanManager.setTravelMode(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DST
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = "توقيت صيفي", tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("دعم التوقيت الصيفي يدويًا", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("إضافة ساعة واحدة لتوقيت الصلاة المسجل.", color = TextColorSecondary, fontSize = 9.sp)
                        }
                    }
                    Switch(
                        checked = daylightSaving,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            daylightSaving = chk
                            AdhanManager.setDaylightSaving(context, chk)
                            AdhanManager.scheduleNextAlarm(context)
                            onRefresh()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }
            }
        }

        // Smart reminders
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "التذكير والسنن الإيمانية 📿",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Post prayer Azkar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تذكير بأذكار بعد الصلاة", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("إشعار لطيف بعد الصلاة بـ 5 دقائق للأذكار المأثورة.", color = TextColorSecondary, fontSize = 9.sp)
                    }
                    Switch(
                        checked = azkarRemind,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            azkarRemind = chk
                            AdhanManager.setPostPrayerAzkarReminder(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Qiyam
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تذكير بصلاة قيام الليل", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("إرسال إشعار في الثلث الأخير من الليل لقيام الليل والدعاء.", color = TextColorSecondary, fontSize = 9.sp)
                    }
                    Switch(
                        checked = qiyamRemind,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            qiyamRemind = chk
                            AdhanManager.setQiyamReminder(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fasting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تذكير بصيام الإثنين والخميس", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("إرسال إشعار ينبه بفضيلة صيام نفل الإثنين والخميس كل أسبوع.", color = TextColorSecondary, fontSize = 9.sp)
                    }
                    Switch(
                        checked = fastingRemind,
                        onCheckedChange = { chk ->
                            viewModel.triggerHaptic()
                            fastingRemind = chk
                            AdhanManager.setFastingReminder(context, chk)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LightWhite, checkedTrackColor = EmeraldSecondary)
                    )
                }
            }
        }
    }
}
