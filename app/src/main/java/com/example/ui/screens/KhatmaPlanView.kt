package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.KhatmaPlan
import com.example.data.KhatmaPlanManager
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel
import com.example.widget.WidgetUpdateHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KhatmaPlanView(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSurah: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var khatmaPlan by remember { mutableStateOf(KhatmaPlanManager.getPlan(context)) }
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showManualPageDialog by remember { mutableStateOf(false) }
    var showDuaaDialog by remember { mutableStateOf(false) }
    var showWidgetGuideDialog by remember { mutableStateOf(false) }

    fun refreshPlan() {
        khatmaPlan = KhatmaPlanManager.getPlan(context)
        viewModel.khatmaProgressPages = khatmaPlan.currentPage
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // TOP HERO PLAN CARD
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("khatma_plan_hero_card"),
            borderColor = GoldAccent.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IslamicStarStarIcon(modifier = Modifier.size(24.dp))
                        Text(
                            text = "خطة ختمة القرآن المخصصة 📖",
                            color = GoldAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Plan badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldSecondary.copy(alpha = 0.2f))
                            .border(1.dp, EmeraldSecondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${khatmaPlan.planDays} يوماً 🗓️",
                            color = EmeraldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الصفحة الحالية: ${khatmaPlan.currentPage} من ٦٠٤",
                            color = LightWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val percent = (khatmaPlan.progressPercent * 100).toInt()
                        Text(
                            text = "نسبة الإنجاز: $percent% • متبقي ${khatmaPlan.remainingPages} صفحة",
                            color = TextColorSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Days remaining badge
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${khatmaPlan.daysRemaining} يوم",
                            color = GoldAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "حتى موعد الختم",
                            color = TextColorSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { khatmaPlan.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = GoldAccent,
                    trackColor = EmeraldMuted.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Plan Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showCreatePlanDialog = true
                            viewModel.triggerHaptic()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldMuted.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل الخطة 🎯", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showDuaaDialog = true
                            viewModel.triggerHaptic()
                        },
                        border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = EmeraldSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("دعاء الختمة 🤲", color = EmeraldSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DAILY WARD (ورد اليوم) CARD
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_ward_card"),
            borderColor = if (khatmaPlan.isTodayGoalCompleted) EmeraldSecondary else CardBorder
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "الورد اليومي",
                            tint = if (khatmaPlan.isTodayGoalCompleted) EmeraldSecondary else GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "الورد اليومي المطلوب 🌿",
                            color = LightWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (khatmaPlan.isTodayGoalCompleted) {
                        Text(
                            text = "أنجزت ورد اليوم بنجاح 🎉",
                            color = EmeraldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${khatmaPlan.dailyTargetPages} صفحات / يوم",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "صفحات قُرئت اليوم: ${khatmaPlan.todayReadCount} من ${khatmaPlan.dailyTargetPages} صفحة",
                    color = TextColorSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Increment Buttons
                Text(
                    text = "تسجيل قراءة الصفحات السريعة:",
                    color = TextColorSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 5, 10).forEach { inc ->
                        Button(
                            onClick = {
                                val next = (khatmaPlan.currentPage + inc).coerceAtMost(604)
                                KhatmaPlanManager.updateProgress(context, next)
                                refreshPlan()
                                viewModel.triggerHaptic()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("+$inc صفحة", color = LightWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            showManualPageDialog = true
                            viewModel.triggerHaptic()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkGlass),
                        border = BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f).height(36.dp)
                    ) {
                        Text("تحديد صفحة ✏️", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DAILY REMINDER CONFIGURATION CARD
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("khatma_reminder_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "تنبيه الورد",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "تنبيه الورد القرآني اليومي ⏰",
                                color = LightWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", khatmaPlan.reminderHour, khatmaPlan.reminderMinute)
                            Text(
                                text = if (khatmaPlan.reminderEnabled) "يصلك يومياً في تمام $timeStr" else "التنبيه اليومي متوقف حالياً",
                                color = if (khatmaPlan.reminderEnabled) EmeraldSecondary else TextColorSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = khatmaPlan.reminderEnabled,
                        onCheckedChange = { checked ->
                            KhatmaPlanManager.setReminderSettings(
                                context,
                                enabled = checked,
                                hour = khatmaPlan.reminderHour,
                                minute = khatmaPlan.reminderMinute
                            )
                            refreshPlan()
                            viewModel.triggerHaptic()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GoldAccent,
                            checkedTrackColor = EmeraldPrimary,
                            uncheckedThumbColor = TextColorSecondary,
                            uncheckedTrackColor = SurfaceDarkGlass
                        )
                    )
                }

                if (khatmaPlan.reminderEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تغيير موعد التنبيه اليومي:",
                            color = TextColorSecondary,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        KhatmaPlanManager.setReminderSettings(context, true, hour, minute)
                                        refreshPlan()
                                        viewModel.triggerHaptic()
                                    },
                                    khatmaPlan.reminderHour,
                                    khatmaPlan.reminderMinute,
                                    false
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعديل الوقت 🕒", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // WIDGET PROMOTION / MANAGEMENT CARD
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("khatma_widget_promo_card"),
            borderColor = GoldAccent.copy(alpha = 0.3f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "الودجات",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ودجات شاشة القفل والرئيسية 📱",
                            color = GoldAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("جديد ✨", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تابع مواقيت الصلاة، العد التنازلي، والذكر اليومي مباشرةً من شاشة القفل والشاشة الرئيسية لهاتفك بدون فتح التطبيق.",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            WidgetUpdateHelper.updateAllWidgets(context)
                            viewModel.triggerHaptic()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = LightWhite, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحديث الودجات 🔄", color = LightWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showWidgetGuideDialog = true
                            viewModel.triggerHaptic()
                        },
                        border = BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("طريقة الإضافة 💡", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // DIALOG: CREATE / CUSTOMIZE PLAN
    if (showCreatePlanDialog) {
        var selectedDays by remember { mutableStateOf(khatmaPlan.planDays) }
        var customDaysInput by remember { mutableStateOf(khatmaPlan.planDays.toString()) }
        var isCustom by remember { mutableStateOf(false) }
        var startPageInput by remember { mutableStateOf(khatmaPlan.currentPage.toString()) }
        var reminderEnabledState by remember { mutableStateOf(khatmaPlan.reminderEnabled) }

        Dialog(onDismissRequest = { showCreatePlanDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.5.dp, GoldAccent, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "تحديد موعد ختمة مخصصة 🗓️",
                        color = GoldAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اختر مدة الختمة وسيقوم التطبيق بحساب الورد اليومي وجدولته لك تلقائياً",
                        color = TextColorSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset Durations Grid
                    Text(
                        text = "المدد الشائعة للختم:",
                        color = LightWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val presets = listOf(
                        Triple(7, "ختمة أسبوعية (٧ أيام)", "~٨٦ ص/يوم"),
                        Triple(15, "ختمة نصف شهر (١٥ يوماً)", "~٤٠ ص/يوم"),
                        Triple(30, "ختمة الشهر (٣٠ يوماً)", "~٢٠ ص/يوم"),
                        Triple(60, "ختمة شهرين (٦٠ يوماً)", "~١٠ ص/يوم")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { preset ->
                            val isSelected = !isCustom && selectedDays == preset.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) GoldAccent.copy(alpha = 0.2f) else EmeraldMuted.copy(alpha = 0.15f))
                                    .border(1.dp, if (isSelected) GoldAccent else CardBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        isCustom = false
                                        selectedDays = preset.first
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.second,
                                    color = if (isSelected) GoldAccent else LightWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.third,
                                    color = EmeraldSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Custom Days Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCustom) GoldAccent.copy(alpha = 0.2f) else EmeraldMuted.copy(alpha = 0.15f))
                                .border(1.dp, if (isCustom) GoldAccent else CardBorder, RoundedCornerShape(10.dp))
                                .clickable { isCustom = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مدة مخصصة أخرى 🎯",
                                color = if (isCustom) GoldAccent else LightWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCustom) {
                                Text("محدد", color = GoldAccent, fontSize = 11.sp)
                            }
                        }
                    }

                    if (isCustom) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("عدد الأيام المطلوب (مثال: 45)", color = TextColorSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Start page
                    OutlinedTextField(
                        value = startPageInput,
                        onValueChange = { startPageInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("صفحة البداية (من 1 إلى 604)", color = TextColorSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val days = if (isCustom) {
                                    (customDaysInput.toIntOrNull() ?: 30).coerceIn(1, 365)
                                } else {
                                    selectedDays
                                }
                                val startPage = (startPageInput.toIntOrNull() ?: 1).coerceIn(1, 604)

                                KhatmaPlanManager.createNewPlan(
                                    context = context,
                                    planDays = days,
                                    startPage = startPage,
                                    reminderHour = khatmaPlan.reminderHour,
                                    reminderMinute = khatmaPlan.reminderMinute,
                                    reminderEnabled = reminderEnabledState
                                )
                                refreshPlan()
                                showCreatePlanDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("حفظ وتفعيل الخطة ✨", color = LightWhite, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showCreatePlanDialog = false },
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.7f).height(44.dp)
                        ) {
                            Text("إلغاء", color = TextColorSecondary)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: MANUAL CURRENT PAGE ENTRY
    if (showManualPageDialog) {
        var pageInput by remember { mutableStateOf(khatmaPlan.currentPage.toString()) }

        Dialog(onDismissRequest = { showManualPageDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, GoldAccent, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "تحديد صفحتك الحالية في المصحف 📖",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الصفحة (1 - 604)", color = TextColorSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val page = (pageInput.toIntOrNull() ?: 1).coerceIn(1, 604)
                                KhatmaPlanManager.updateProgress(context, page)
                                refreshPlan()
                                showManualPageDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("تحديث الصفحة", color = LightWhite, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showManualPageDialog = false },
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.7f).height(40.dp)
                        ) {
                            Text("إلغاء", color = TextColorSecondary)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: DUAA AL-KHATM
    if (showDuaaDialog) {
        Dialog(onDismissRequest = { showDuaaDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.5.dp, GoldAccent, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IslamicStarStarIcon(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دعاء ختم القرآن الكريم 🤲",
                        color = GoldAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
                    Spacer(modifier = Modifier.height(12.dp))

                    val duaaText = """
اللَّهُمَّ ارْحَمْنِي بالقُرْآنِ وَاجْعَلهُ لِي إِمَاماً وَنُوراً وَهُدًى وَرَحْمَةً.

اللَّهُمَّ ذَكِّرْنِي مِنْهُ مَا نَسِيتُ وَعَلِّمْنِي مِنْهُ مَا جَهِلْتُ وَارْزُقْنِي تِلاَوَتَهُ آنَاءَ اللَّيْلِ وَأَطْرَافَ النَّهَارِ وَاجْعَلْهُ لِي حُجَّةً يَا رَبَّ العَالَمِينَ.

اللَّهُمَّ أَصْلِحْ لِي دِينِي الَّذِي هُوَ عِصْمَةُ أَمْرِي، وَأَصْلِحْ لِي دُنْيَايَ الَّتِي فِيهَا مَعَاشِي، وَأَصْلِحْ لِي آخِرَتِي الَّتِي فِيهَا مَعَادِي، وَاجْعَلِ الحَيَاةَ زِيَادَةً لِي فِي كُلِّ خَيْرٍ وَاجْعَلِ المَوْتَ رَاحَةً لِي مِنْ كُلِّ شَرٍّ.

اللَّهُمَّ اجْعَلْ خَيْرَ عُمْرِي آخِرَهُ وَخَيْرَ عَمَلِي خَوَاتِمَهُ وَخَيْرَ أَيَّامِي يَوْمَ أَلْقَاكَ فِيهِ.

اللَّهُمَّ إِنِّي أَسْأَلُكَ عِيشَةً هَنِيَّةً وَمِيتَةً سَوِيَّةً وَمَرَدًّا غَيْرَ مُخْزٍ وَلاَ فَاضِحٍ.

اللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ المَسْأَلَةِ وَخَيْرَ الدُّعَاءِ وَخَيْرَ النَّجَاحِ وَخَيْرَ العِلْمِ وَخَيْرَ العَمَلِ وَخَيْرَ الثَّوَابِ وَخَيْرَ الحَيَاةِ وَخَيْرَ المَمَاتِ.
                    """.trimIndent()

                    Text(
                        text = duaaText,
                        color = TextColorPrimary,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDuaaDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("تقبل الله منا ومنكم 🤲", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // DIALOG: WIDGET GUIDE
    if (showWidgetGuideDialog) {
        Dialog(onDismissRequest = { showWidgetGuideDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, GoldAccent, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "كيفية إضافة الودجات لهاتفك 📱",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1️⃣", fontSize = 14.sp)
                            Text("اضغط مطولاً على أي مساحة فارغة في الشاشة الرئيسية لهاتفك.", color = TextColorPrimary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("2️⃣", fontSize = 14.sp)
                            Text("اختر «التطبيقات المصغرة» أو «Widgets».", color = TextColorPrimary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3️⃣", fontSize = 14.sp)
                            Text("ابحث عن تطبيق «إيمانيات» واختر ودجت مواقيت الصلاة أو الذكر اليومي.", color = TextColorPrimary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("4️⃣", fontSize = 14.sp)
                            Text("اسحب الودجت وضعه في الشاشة الرئيسية أو شاشة القفل بالحجم المناسب لك.", color = TextColorPrimary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showWidgetGuideDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text("فهمت ذلك ✔️", color = LightWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
