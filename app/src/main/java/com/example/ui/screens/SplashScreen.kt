package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.UpdateInfo
import com.example.data.UpdateManager
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfoState by remember { mutableStateOf<UpdateInfo?>(null) }
    var isUpdateForced by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    
    // Animation for rotating Islamic star emblem
    val infiniteTransition = rememberInfiniteTransition(label = "OrnamentRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Animation for fade-in & scale-up of logo
    var startAnimationState by remember { mutableStateOf(false) }
    val entryAlpha by animateFloatAsState(
        targetValue = if (startAnimationState) 1f else 0f,
        animationSpec = tween(1500, easing = EaseOutBack),
        label = "FadeIn"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (startAnimationState) 1f else 0.7f,
        animationSpec = tween(1500, easing = EaseOutBack),
        label = "ScaleUp"
    )

    LaunchedEffect(Unit) {
        startAnimationState = true
        val startTime = System.currentTimeMillis()
        
        // Asynchronously check updates
        var updateResult: UpdateInfo? = null
        try {
            updateResult = withContext(Dispatchers.IO) {
                UpdateManager.fetchUpdateInfo(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        // Require splash display for at least 2.5 seconds for visual harmony
        if (duration < 2500) {
            delay(2500 - duration)
        }

        if (updateResult != null) {
            val currentCode = UpdateManager.getCurrentVersionCode(context)
            if (updateResult.latestVersionCode > currentCode) {
                val forced = currentCode < updateResult.minRequiredVersionCode || updateResult.isForceUpdate
                updateInfoState = updateResult
                isUpdateForced = forced
                showUpdateDialog = true
            } else {
                onSplashFinished()
            }
        } else {
            // Null implies network failure, timeout, or invalid JSON. Proceed in Offline Mode.
            alertMessage = "يتعذر الاتصال بالخادم للتحقق من التحديثات، جاري العمل بالوضع غير المتصل..."
            delay(1500)
            onSplashFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background ornament geometry
        ElegantBackgroundPattern(alpha = 0.08f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Islamic Accent Logo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(rotationAngle)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(SurfaceDarkGlass)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                IslamicStarStarIcon(
                    modifier = Modifier.fillMaxSize(),
                    color = GoldAccent,
                    strokeWidth = 4f
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dynamic text greeting
            Text(
                text = "إيمانيات",
                color = GoldAccent,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 38.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "رفيقك الإيماني والروحاني المتكامل",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            IslamicOrnamentDivider(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status message
            if (showUpdateDialog) {
                Text(
                    text = "يتوفر تحديث جديد للتطبيق! يرجى المتابعة ✨",
                    color = GoldAccent.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = EmeraldSecondary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = alertMessage ?: "جاري تهيئة النفحات الروحانية والتحقق من التحديثات...",
                        color = TextColorSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Beautiful Footer Info
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "الإصدار الحالي: ${UpdateManager.getCurrentVersionName(context)} (${UpdateManager.getCurrentVersionCode(context)})",
                color = TextColorSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
        }
    }

    // Modern custom update Dialog
    if (showUpdateDialog && updateInfoState != null) {
        UpdateAlertDialog(
            updateInfo = updateInfoState!!,
            isForced = isUpdateForced,
            onDismissRequest = {
                if (!isUpdateForced) {
                    showUpdateDialog = false
                    onSplashFinished()
                }
            }
        )
    }
}

@Composable
fun UpdateAlertDialog(
    updateInfo: UpdateInfo,
    isForced: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = !isForced,
            dismissOnClickOutside = !isForced
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 24.dp)
                .shadow(24.dp, shape = RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Visual Indicator
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldPrimary.copy(alpha = 0.3f), EmeraldMuted.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "تحقق من التحديثات",
                        tint = GoldAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = if (isForced) "تحديث إجباري هام! ⚠️" else "إصدار جديد متوفر الآن! ✨",
                    color = if (isForced) GoldAccent else GoldAccent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version indicators
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldMuted.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الإصدار الجديد: V${updateInfo.latestVersionName}",
                        color = EmeraldSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(رمز: ${updateInfo.latestVersionCode})",
                        color = TextColorSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog container
                Text(
                    text = "ما الجديد في هذا الإصدار:",
                    color = TextColorPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground.copy(alpha = 0.5f))
                        .border(BorderStroke(0.5.dp, CardBorder), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val changelogText = updateInfo.changelogAr.ifBlank { updateInfo.changelogEn }.ifBlank { "• تحسينات عامة لثبات واستقرار التطبيق." }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.End
                    ) {
                        changelogText.split("\n").forEach { line ->
                            if (line.isNotBlank()) {
                                Text(
                                    text = line.trim(),
                                    color = TextColorSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isForced) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MutedRed.copy(alpha = 0.15f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "هذا التحديث إجباري لضمان استمرار عمل الخدمات بشكل صحيح وأكثر أماناً.",
                            color = MutedRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "هام للغاية",
                            tint = MutedRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.updateUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "يبدو أن رابط التحديث غير متاح حاليًا.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "تحديث الآن 🚀",
                            color = LightWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isForced) {
                        OutlinedButton(
                            onClick = { onDismissRequest() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColorSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "تحديث لاحقاً",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
