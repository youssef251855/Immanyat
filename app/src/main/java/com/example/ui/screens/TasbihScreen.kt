package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TasbihScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val tasbihList by viewModel.tasbihItems.collectAsState()
    val activeItem = viewModel.activeTasbihItem ?: tasbihList.firstOrNull()

    var showAddDialog by remember { mutableStateOf(false) }
    var addCustomNameInput by remember { mutableStateOf("") }
    var addCustomTargetInput by remember { mutableStateOf(100) }

    // Sound alert mode state
    var soundModeOn by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER SCREEN
            Text(
                text = "السبحة الإلكترونية",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "سبّح لله في الغدو والآصال - تتبع تسبيحاتك وحافظ على وردك",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PRESETS SELECTOR ROW OR GRID
            Text(
                text = "اختر صيغة الذكر",
                color = LightWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tasbihList.forEach { item ->
                    val isSelected = activeItem?.id == item.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) EmeraldPrimary
                                else EmeraldMuted.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) GoldAccent else CardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                viewModel.selectTasbihItem(item)
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.name,
                            color = if (isSelected) LightWhite else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Add button preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldAccent.copy(alpha = 0.15f))
                        .border(1.dp, GoldAccent, RoundedCornerShape(12.dp))
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "+ إضافة صيغة مخصصة",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // LARGE 3D NEUMORPHIC COUNT SURFACE
            activeItem?.let { item ->
                val progress = item.count.toFloat() / item.totalRequired.toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(1.0f + (progress * 0.05f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        EmeraldPrimary.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Counter Core Ring
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(SurfaceDarkGlass)
                            .border(1.5.dp, GoldAccent.copy(alpha = 0.15f), CircleShape)
                            .clickable {
                                viewModel.incrementTasbih(item.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Progress ring overlay
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = GoldAccent,
                            strokeWidth = 8.dp,
                            modifier = Modifier.fillMaxSize(),
                            trackColor = CardBorder
                        )

                        // Central details
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = item.name,
                                color = TextColorSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.count.toString(),
                                color = LightWhite,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "/ ${item.totalRequired}",
                                color = GoldAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "اضغط للعد",
                                color = EmeraldSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldSecondary.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action controllers (Reset & Sounds indicators)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    OutlinedButton(
                        onClick = { viewModel.resetTasbih(item.id) },
                        border = BorderStroke(1.dp, MutedRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "تصفير", tint = MutedRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصفير العداد", color = MutedRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Sound Toggle Button
                    IconButton(
                        onClick = { soundModeOn = !soundModeOn }
                    ) {
                        Icon(
                            imageVector = if (soundModeOn) Icons.Outlined.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "تنبيه صوتي",
                            tint = if (soundModeOn) GoldAccent else TextColorSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Set target dialog indicator
                    Box(
                        modifier = Modifier
                            .background(EmeraldMuted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "المكتمل: ${(progress * 100).toInt()}%",
                            color = EmeraldSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
            Spacer(modifier = Modifier.height(10.dp))

            // TASBIH HISTORY DISPLAY STATS (CUSTOM CANVAS SPLINE GRAPH)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "إحصائيات التسبيح الأسبوعية (٢٠٢٦)",
                    color = GoldAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تتبع ثباتك في تكرار التسبيحات على مدار الأيام الماضية",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Custom Canvas drawing for a elegant spine chart
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val points = listOf(
                        Offset(width * 0.1f, height * 0.82f),
                        Offset(width * 0.25f, height * 0.55f),
                        Offset(width * 0.4f, height * 0.65f),
                        Offset(width * 0.55f, height * 0.35f),
                        Offset(width * 0.7f, height * 0.48f),
                        Offset(width * 0.85f, height * 0.22f),
                        Offset(width * 0.95f, height * 0.15f)
                    )

                    // Draw reference grid lines
                    drawLine(
                        color = CardBorder,
                        start = Offset(0f, height * 0.9f),
                        end = Offset(width, height * 0.9f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = CardBorder.copy(alpha = 0.5f),
                        start = Offset(0f, height * 0.5f),
                        end = Offset(width, height * 0.5f),
                        strokeWidth = 1f
                    )

                    // Draw the area brush under the line
                    val fillPath = Path().apply {
                        moveTo(points.first().x, height * 0.9f)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, height * 0.9f)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EmeraldPrimary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw the spline line
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = strokePath,
                        color = EmeraldSecondary,
                        style = Stroke(width = 4f)
                    )

                    // Draw points on the graph
                    points.forEach { pt ->
                        drawCircle(color = GoldAccent, radius = 5f, center = pt)
                        drawCircle(color = DarkBackground, radius = 2f, center = pt)
                    }
                }

                // Days axis row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val axisDays = listOf("الجمعة", "السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "اليوم")
                    axisDays.forEach { dayName ->
                        Text(
                            text = dayName,
                            color = TextColorSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // margin bottom
        }

        // ADD CUSTOM TASBIH FORM DIALOG popup
        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDarkGlass)
                        .border(1.dp, GoldAccent, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "إضافة صيغة تسبيح جديدة",
                            color = GoldAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("الصيغة اللفظية للذكر", color = TextColorSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = addCustomNameInput,
                            onValueChange = { addCustomNameInput = it },
                            placeholder = { Text("مثال: سبحان الله وبحمده سبحان الله العظيم", color = TextColorSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("العدد المستهدف (الهدف)", color = TextColorSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(33, 100, 1000).forEach { num ->
                                val active = addCustomTargetInput == num
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (active) EmeraldPrimary else EmeraldMuted.copy(alpha = 0.2f)
                                        )
                                        .border(1.dp, if (active) GoldAccent else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable { addCustomTargetInput = num }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = num.toString(),
                                        color = if (active) LightWhite else TextColorSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showAddDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("إلغاء", color = LightWhite, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (addCustomNameInput.isNotEmpty()) {
                                        viewModel.addNewTasbih(addCustomNameInput, addCustomTargetInput)
                                        addCustomNameInput = ""
                                        showAddDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                            ) {
                                Text("حفظ الورد", color = LightWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
