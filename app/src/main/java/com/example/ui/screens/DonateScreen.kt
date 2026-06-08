package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel
import com.example.api.PayPalApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class DonateSubTab {
    VIP_GOLD,
    QIBLA_PRAYERS,
    NAMES_OF_ALLAH,
    ZAKAT_CALC,
    HADITH_STUDIO
}

@Composable
fun DonateScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableStateOf(DonateSubTab.VIP_GOLD) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Ambient Islamic back ornament 
        ElegantBackgroundPattern(alpha = 0.04f)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant horizontal Scrollable Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkGlass)
                    .border(BorderStroke(0.5.dp, CardBorder))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                DonateSubTab.values().forEach { tab ->
                    val isSelected = activeSubTab == tab
                    val label = when (tab) {
                        DonateSubTab.VIP_GOLD -> "🏆 النسخة الذهبية"
                        DonateSubTab.QIBLA_PRAYERS -> "🧭 القبلة والصلوات"
                        DonateSubTab.NAMES_OF_ALLAH -> "✨ أسماء الله"
                        DonateSubTab.ZAKAT_CALC -> "💰 حاسبة الزكاة"
                        DonateSubTab.HADITH_STUDIO -> "📜 حديث وحكمة"
                    }

                    val tabBgBrush = if (isSelected) {
                        Brush.horizontalGradient(listOf(EmeraldPrimary, EmeraldSecondary))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tabBgBrush)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) GoldAccent else CardBorder.copy(alpha = 0.5f)
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                viewModel.triggerHaptic()
                                activeSubTab = tab
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) LightWhite else TextColorSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Sub-screen Container with crisp entry transformations
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeSubTab) {
                    DonateSubTab.VIP_GOLD -> VipGoldTab(viewModel = viewModel)
                    DonateSubTab.QIBLA_PRAYERS -> QiblaAndPrayersTab(viewModel = viewModel)
                    DonateSubTab.NAMES_OF_ALLAH -> NamesOfAllahTab(viewModel = viewModel)
                    DonateSubTab.ZAKAT_CALC -> ZakatCalculatorTab(viewModel = viewModel)
                    DonateSubTab.HADITH_STUDIO -> HadithStudioTab(viewModel = viewModel)
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 1: VIP PLATINUM SUBSCRIPTION & AD BLOCKING
// ---------------------------------------------------------
@Composable
fun VipGoldTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var clickCount by remember { mutableStateOf(getStoredDonationClicks(context)) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showPaypalDialog by remember { mutableStateOf(false) }
    var isGuideExpanded by remember { mutableStateOf(false) }
    
    // Pro Subscription states
    var selectedPlanIndex by remember { mutableStateOf(1) } // Default to Yearly Pro (Best Value)
    val planNames = listOf("الباقة الشهرية (Pro Monthly)", "الباقة السنوية (Pro Yearly)", "الباقة الماسية (Lifetime Pro)")
    val planPrices = listOf("4.99$", "19.99$", "49.99$")
    val planPeriods = listOf("شهر للتقسيط", "سنة كاملة (وفر 65%)", "مدى الحياة (مرة واحدة)")
    val planDetails = listOf(
        "مناسب لتجربة الخدمة وسداد الرسوم السحابية لشهر واحد.",
        "الخيار الأكثر شعبية! دعم مستمر لعام كامل مع ترقية كاملة الفوائد.",
        "الاستثمار الإيماني الأعظم! دعم دائم للمشروع وفتح المزايا بلا حدود مدى الحياة."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing Golden Card (Subscription Status Display)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(
                        1.5.dp,
                        if (viewModel.isVipPremiumActive) GoldAccent else Color.Transparent
                    ),
                    RoundedCornerShape(20.dp)
                )
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ElegantBackgroundPattern(alpha = 0.05f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldAccent.copy(alpha = 0.3f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "التاج الذهبي",
                            tint = GoldAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (viewModel.isVipPremiumActive) "الاشتراك الاحترافي مفعل: خطة PRO 👑" else "باقة إيمانيات الاحترافية (Emaniat Pro) ✨",
                        color = GoldAccent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (viewModel.isVipPremiumActive) {
                            "جزاك الله خيراً! أنت تستمتع الآن بكامل الميزات الاحترافية والتطبيق خالٍ تماماً من الإعلانات لدعمكم النبيل."
                        } else {
                            "اشترك الآن في خطة Pro لإلغاء الإعلانات المنبثقة، وتفعيل التاج الذهبي، والحصول على وصول كامل غير محدود لجميع مزايا التطبيق الذكية."
                        },
                        color = TextColorPrimary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    if (viewModel.isVipPremiumActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(30.dp))
                                .background(GoldAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = GoldAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "حالة الاشتراك: فعال (دعم ذهبي) 🏅",
                                color = GoldAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { 
                                viewModel.setVipPremiumStatus(false)
                                Toast.makeText(context, "تم تبديل العضوية للمجانية للاختبار والتجربة", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColorSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("الرجوع للمجاني لتجربة الدفع مرة أخرى", fontSize = 10.sp)
                        }

                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // PLAN SELECTOR GRID (3 Plans)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (i in 0..2) {
                                val isSelected = selectedPlanIndex == i
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) EmeraldMuted.copy(alpha = 0.25f) else Color.Transparent)
                                        .border(
                                            BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) GoldAccent else CardBorder
                                              ),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.triggerHaptic()
                                            selectedPlanIndex = i
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (i == 1) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(end = 6.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(GoldAccent)
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("الأكثر توفيراً 🔥", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(
                                                    text = planNames[i],
                                                    color = if (isSelected) GoldAccent else LightWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Right
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = planDetails[i],
                                                color = TextColorSecondary,
                                                fontSize = 9.sp,
                                                textAlign = TextAlign.Right
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = planPrices[i],
                                                color = if (isSelected) GoldAccent else LightWhite,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = planPeriods[i],
                                                color = TextColorSecondary,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // PAYPAL PAYMENT EMULATOR TRICOLOR BUTTON
                        Button(
                            onClick = {
                                viewModel.triggerHaptic()
                                showPaypalDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC439)), // Official PayPal Yellow
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = "PayPal Logo",
                                tint = Color(0xFF003087) // Official PayPal Dark Blue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "شراء خطة PRO عبر PayPal الذكي 💳",
                                color = Color(0xFF003087),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // CREDIT CARD / GOOGLE PLAY BUTTON
                            Button(
                                onClick = {
                                    viewModel.triggerHaptic()
                                    showCheckoutDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "الدفع بالبطاقة 💳",
                                    color = LightWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // FREE SPONSOR AD TRIGGER
                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerHaptic()
                                    try {
                                        val adUrl = "https://www.effectivecpmnetwork.com/wykven1z2g?key=8354f640db8eebe8bf7568da45909e36"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                                        context.startActivity(intent)

                                        val newCount = clickCount + 1
                                        clickCount = newCount
                                        saveDonationClicks(context, newCount)

                                        viewModel.setVipPremiumStatus(true)
                                        Toast.makeText(context, "جزاك الله خيراً! لزيارتكم إعلان الرعاية، تم تفعيل خطة Pro مجاناً بالكامل مؤقتاً! 🎉", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "حدث خطأ أثناء الاتصال بالخادم.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "تفعيل بزيارة إعلان دعم 🔓",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Perks comparison section
        Text(
            text = "المزايا الحصرية لمشتركي خطة Pro 👑",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            textAlign = TextAlign.Right
        )

        val perks = listOf(
            "إيقاف تام وفوري لكافة الإعلانات المنبثقة والبينية في شاشات التطبيق.",
            "وسام العضو المحترف PRO الذهبي بملفك الشخصي وبطاقة الإحصائيات.",
            "وصول كامل وغير محدود لجميع معالم الواحة الإيمانية (القبلة، أسماء الله، الحاسبة).",
            "تسجيل كتابة الأفكار وتدبرات الآيات بنظام الحفظ السحابي اللانهائي.",
            "المساهمة المباشرة في تمويل سيرفرات المصحف الإلكتروني وصوتيات كبار القراء."
        )

        perks.forEach { perk ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = perk,
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // DEVELOPER SECTION: EDUCATIONAL PAYPAL ROADMAP GUIDE (Perfect for users and developer testing!)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, if (isGuideExpanded) GoldAccent else CardBorder), RoundedCornerShape(12.dp))
                .clickable {
                    viewModel.triggerHaptic()
                    isGuideExpanded = !isGuideExpanded
                },
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = if (isGuideExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = GoldAccent
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "للمطورين: دليل دمج PayPal حقيقي في الأندرويد 🛠️",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help Guide",
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isGuideExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "لدمج بوابة الدفع PayPal في تطبيق أندرويد حقيقي لرفعه على جوجل بلاي، اتبع الخطوات البرمجية التالية:",
                        color = LightWhite,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "١. إضافة الاعتمادية في build.gradle.kts (App Module):\n" +
                               "implementation(\"com.paypal.checkout:checkout-sdk:1.2.0\")\n\n" +
                               "٢. تهيئة الـ SDK في كلاس Application الخاص بالتطبيق:\n" +
                               "class MyEmaniatApp : Application() {\n" +
                               "    override fun onCreate() {\n" +
                               "        super.onCreate()\n" +
                               "        val config = CheckoutConfig(\n" +
                               "            application = this,\n" +
                               "            clientId = \"YOUR_PAYPAL_SANDBOX_CLIENT_ID\",\n" +
                               "            environment = Environment.SANDBOX,\n" +
                               "            returnUrl = \"com.aistudio.emaniat://paypalpay\"\n" +
                               "        )\n" +
                               "        PayPalCheckout.setConfig(config)\n" +
                               "    }\n" +
                               "}\n\n" +
                               "٣. كتابة دالة الدفع داخل الكومبوزبل (Checkout Click Listener):\n" +
                               "fun startPayPalPayment(amountValue: String) {\n" +
                               "    PayPalCheckout.startCheckout(CreateOrder { actions ->\n" +
                               "        val purchaseUnit = PurchaseUnit.Builder()\n" +
                               "            .amount(Amount.Builder().value(amountValue).currencyCode(CurrencyCode.USD).build())\n" +
                               "            .build()\n" +
                               "        actions.create(Order.Builder().purchaseUnits(listOf(purchaseUnit)).build())\n" +
                               "    })\n" +
                               "}\n\n" +
                               "٤. تسجيل المستمعين (Callbacks) المعتمدين لتحديث خادمك الخاص:\n" +
                               "PayPalCheckout.registerCallbacks(\n" +
                               "    onApprove = OnApprove { approval ->\n" +
                               "        // تم الدفع بنجاح! حدث قاعدة البيانات السحابية\n" +
                               "        approval.orderActions.capture { result ->\n" +
                               "            Log.d(\"PayPal\", \"Order captured successfully: \${result.orderId}\")\n" +
                               "        }\n" +
                               "    },\n" +
                               "    onCancel = OnCancel {\n" +
                               "        // تم إلغاء عملية الدفع من قبل المستخدم\n" +
                               "    }\n" +
                               ")",
                        color = EmeraldSecondary,
                        fontSize = 9.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Left,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground)
                            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
            }
        }
    }

    // INTERACTIVE PAYPAL ONLINE SANDBOX PORTAL DIALOG
    if (showPaypalDialog) {
        var paypalEmail by remember { mutableStateOf("") }
        var paypalPassword by remember { mutableStateOf("") }
        var isPaypalProcessing by remember { mutableStateOf(false) }
        var paypalStep by remember { mutableStateOf(1) } // API: 1: Ready, 2: Linked/Awaiting Capture, 3: Success. Simulated: 1: Login, 2: Review, 3: Success
        
        // Real API state holders
        var realOrderInfo by remember { mutableStateOf<com.example.api.PayPalOrderInfo?>(null) }
        var paypalAccessToken by remember { mutableStateOf("") }
        
        val targetPrice = planPrices[selectedPlanIndex]
        val targetPlan = planNames[selectedPlanIndex]
        val isRealApi = PayPalApiClient.isConfigured

        AlertDialog(
            onDismissRequest = { if (!isPaypalProcessing) showPaypalDialog = false },
            title = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF003087))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Connection",
                                tint = Color(0xFFFFC439),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRealApi) "PayPal API Integration" else "PayPal Secure Simulator",
                                color = LightWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "PayPal",
                            color = Color(0xFFFFC439),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isPaypalProcessing) {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(
                            color = Color(0xFF0079C1),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (paypalStep) {
                                1 -> "جاري الاتصال بخادم PayPal الآمن وحجز الفاتورة..."
                                2 -> "جاري التحقق من حالة الفاتورة وتأكيد تفويض الدفع..."
                                else -> "تحديث حالة الترخيص بنجاح..."
                            },
                            color = TextColorPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    } else {
                        if (isRealApi) {
                            // REAL API INTEGRATION FLOW
                            when (paypalStep) {
                                1 -> {
                                    Text(
                                        text = "المستلم الأمني: Emaniat Application Inc.",
                                        color = TextColorSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "تفعيل باقة الاحتراف ($targetPlan) حقيقياً بسعر $targetPrice عبر واجهة ربط PayPal المباشرة:",
                                        color = TextColorPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkBackground)
                                            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = targetPrice, color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "الباقة: $targetPlan", color = LightWhite, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = "PayPal REST API v2", color = EmeraldSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "طريقة الإتمام", color = TextColorSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "انقر على زر 'طلب فاتورة PayPal' لإنشاء سجل معاملة معلق مباشرة في خوادمهم والدفع الآمن.",
                                        color = TextColorSecondary,
                                        fontSize = 9.sp,
                                        lineHeight = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                2 -> {
                                    Text(
                                        text = "تم إنشاء معاملة PayPal المعلقة بنجاح! 💳",
                                        color = EmeraldSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "الرجاء النقر على الزر الأصفر أدناه لإتمام توقيع المعاملة وسدادها في صفحة باي بال الآمنة، ثم عد فوراً إلى التطبيق واضغط على 'تأكيد وتفعيل الاشتراك'.",
                                        color = TextColorPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "معاملة باي بال: ${realOrderInfo?.orderId ?: "غير متوفر"}",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    Button(
                                        onClick = {
                                            realOrderInfo?.approvalUrl?.let { url ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC439)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payment,
                                            contentDescription = "Pay Link",
                                            tint = Color(0xFF003087)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "إتمام الدفع في بوابة PayPal الآمنة 🌐",
                                            color = Color(0xFF003087),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                3 -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0x1500FF00))
                                            .border(0.5.dp, Color.Green, RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "تهانينا الحارة! تم الدفع الفعلي والترقية التلقائية بنجاح! 🎉👑\nرصيد حسابك Emaniat Pro مفعل دائماً.",
                                            color = EmeraldSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            // LOCAL SECURE SIMULATION FLOW
                            when (paypalStep) {
                                1 -> { // Credentials Step
                                    Text(
                                        text = "تسجيل الدخول إلى حساب PayPal للمحاكاة وتفعيل $targetPlan بسعر $targetPrice:",
                                        color = TextColorPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = paypalEmail,
                                        onValueChange = { paypalEmail = it },
                                        label = { Text("البريد الإلكتروني لحساب PayPal") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = paypalPassword,
                                        onValueChange = { paypalPassword = it },
                                        label = { Text("كلمة المرور المشفرة") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المستلم: Emaniat Application Inc.",
                                            color = TextColorSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0x20FFA500))
                                            .border(0.5.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "💡 لتفعيل الدفع والترقية التلقائية الحقيقية عبر حساب باي بال الخاص بك، أدخل PAYPAL_CLIENT_ID و PAYPAL_CLIENT_SECRET في لوحة الأسرار (Secrets) على AI Studio.",
                                            color = GoldAccent,
                                            fontSize = 9.sp,
                                            lineHeight = 14.sp,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                2 -> { // Order Review Step
                                    Text(
                                        text = "مراجعة تفاصيل عملية تفويض الدفع الآمنة (المحاكاة):",
                                        color = GoldAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkBackground)
                                            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = targetPrice, color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "الباقة المختارة: $targetPlan", color = LightWhite, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = "PayPal Wallet (Sandbox)", color = EmeraldSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "رصيد مصدر الدفع", color = TextColorSecondary, fontSize = 10.sp)
                                            }
                                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = CardBorder)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = targetPrice, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "الإجمالي المستقطع بالدولار", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "بالنقر على زر 'تأكيد ودفع' أدناه، ستقوم بإتمام محاكاة تفويض المعاملة والحصول على رخصة Pro بالبرمجة المباشرة.",
                                        color = TextColorSecondary,
                                        fontSize = 9.sp,
                                        lineHeight = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isPaypalProcessing) {
                    if (isRealApi) {
                        // Real payment buttons
                        if (paypalStep == 1) {
                            Button(
                                onClick = {
                                    viewModel.triggerHaptic()
                                    isPaypalProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            val token = PayPalApiClient.getAccessToken()
                                            if (token != null) {
                                                paypalAccessToken = token
                                                val cleanPrice = targetPrice.replace("$", "").trim()
                                                val order = PayPalApiClient.createOrder(token, cleanPrice, targetPlan)
                                                if (order != null) {
                                                    realOrderInfo = order
                                                    paypalStep = 2
                                                } else {
                                                    Toast.makeText(context, "فشل إنشاء المعاملة على خادم باي بال.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "فشل المصادقة مع باي بال. تأكد من صحة بيانات API الملقمة في Secrets.", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "خطأ بالشبكة: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isPaypalProcessing = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC439))
                            ) {
                                Text(
                                    text = "طلب فاتورة PayPal 🚀",
                                    color = Color(0xFF003087),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        } else if (paypalStep == 2) {
                            Button(
                                onClick = {
                                    viewModel.triggerHaptic()
                                    val order = realOrderInfo
                                    if (order == null) {
                                        Toast.makeText(context, "لم يتم العثور على معاملة سارية.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isPaypalProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            val captured = PayPalApiClient.captureOrder(paypalAccessToken, order.orderId)
                                            if (captured) {
                                                viewModel.setVipPremiumStatus(true)
                                                paypalStep = 3
                                                Toast.makeText(context, "تم تأكيد الدفع الفعلي بنجاح وتفعيل رخصة الاحتراف للبرنامج! 🎉👑", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "لم نتمكن من التقاط الدفعة. تأكد من إتمام الموافقة والدفع بالرابط أولاً.", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "خطأ في التحقق والالتقاط: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isPaypalProcessing = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text(
                                    text = "تأكيد وتفعيل الاشتراك 👑",
                                    color = LightWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        } else if (paypalStep == 3) {
                            Button(
                                onClick = { showPaypalDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("إغلاق النافذة ☘️", color = LightWhite)
                            }
                        }
                    } else {
                        // Simulated payment buttons
                        Button(
                            onClick = {
                                if (paypalStep == 1) {
                                    if (!paypalEmail.contains("@") || paypalPassword.length < 5) {
                                        Toast.makeText(context, "الرجاء كتابة بريد إلكتروني صالح وكلمة مرور آمنة للمحاكاة", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.triggerHaptic()
                                    isPaypalProcessing = true
                                    coroutineScope.launch {
                                        delay(1500)
                                        isPaypalProcessing = false
                                        paypalStep = 2
                                    }
                                } else if (paypalStep == 2) {
                                    viewModel.triggerHaptic()
                                    isPaypalProcessing = true
                                    coroutineScope.launch {
                                        delay(2000)
                                        isPaypalProcessing = false
                                        showPaypalDialog = false
                                        viewModel.setVipPremiumStatus(true)
                                        Toast.makeText(context, "جزاكم الله خيراً! تم تأكيد تفويض PayPal بنجاح وترقية حسابك إلى باقة PRO الاحترافية مدى الحياة! 🎉👑", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC439))
                        ) {
                            Text(
                                text = if (paypalStep == 1) "متابعة الحساب" else "تأكيد الدفع وشحن رصيد Pro 🚀",
                                color = Color(0xFF003087),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            dismissButton = {
                if (!isPaypalProcessing && paypalStep != 3) {
                    TextButton(onClick = { showPaypalDialog = false }) {
                        Text("إلغاء المعاملة", color = MutedRed)
                    }
                }
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }


    // Checkout credit card simulation dialog
    if (showCheckoutDialog) {
        var cardNumber by remember { mutableStateOf("") }
        var cardExpiry by remember { mutableStateOf("") }
        var cardCvv by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showCheckoutDialog = false },
            title = {
                Text(
                    text = "شراء العضوية الاحترافية (محاكاة البطاقة) 💳",
                    color = GoldAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "أدخل بيانات بطاقة افتراضية لتفعيل اشتراك PRO فوراً بالدفع الرمزي المحاكي:",
                        color = TextColorPrimary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = {
                            if (it.length <= 16 && it.all { char -> char.isDigit() }) cardNumber = it
                        },
                        label = { Text("رقم البطاقة (16 رقم)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cardCvv,
                            onValueChange = {
                                if (it.length <= 3 && it.all { char -> char.isDigit() }) cardCvv = it
                            },
                            label = { Text("رمز CVV") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) cardExpiry = it
                            },
                            label = { Text("صلاحية تجريبية (MMYY)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            color = GoldAccent,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cardNumber.length < 16) {
                            Toast.makeText(context, "الرجاء كتابة رقم بطاقة وخصائص صالحة لعملية الترقية", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.triggerHaptic()
                        isProcessing = true
                        coroutineScope.launch {
                            delay(1200)
                            isProcessing = false
                            showCheckoutDialog = false
                            viewModel.setVipPremiumStatus(true)
                            Toast.makeText(context, "تمت الترقية لمحاكاة عملية الدفع بالبطاقة وشحن الترخيص PRO بنجاح! 👑🎉", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    enabled = !isProcessing
                ) {
                    Text("إتمام الدفع", color = LightWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCheckoutDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("إلغاء", color = TextColorSecondary)
                }
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ---------------------------------------------------------
// FEATURE 2: INTERACTIVE PRAYER CHECKER & COMPASS WIDGET
// ---------------------------------------------------------
@Composable
fun QiblaAndPrayersTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Persistent state for checklist
    val prefs = remember { context.getSharedPreferences("emaniat_prayers", Context.MODE_PRIVATE) }
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var fajrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_fajr", false)) }
    var dhuhrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_dhuhr", false)) }
    var asrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_asr", false)) }
    var maghribChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_maghrib", false)) }
    var ishaChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_isha", false)) }

    var prayerStreak by remember { mutableStateOf(prefs.getInt("prayer_streak", 0)) }

    // Compass simulation / real device rotation sensor
    var rawAzimuth by remember { mutableStateOf(0f) }
    var isSensorAvailable by remember { mutableStateOf(false) }

    // Manual compass calibration in emulator override
    var manualOffset by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    rawAzimuth = event.values[0]
                    isSensorAvailable = true
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensorManager != null && sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val animatedRotation by animateFloatAsState(
        targetValue = if (isSensorAvailable) -rawAzimuth else manualOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compassRotation"
    )

    fun updateStreak() {
        // Simple streak logic: if all 5 prayers checked, streak increments
        val allChecked = fajrChecked && dhuhrChecked && asrChecked && maghribChecked && ishaChecked
        if (allChecked) {
            val lastStreakDay = prefs.getString("last_streak_day", "")
            if (lastStreakDay != todayKey) {
                prayerStreak += 1
                prefs.edit().putInt("prayer_streak", prayerStreak).putString("last_streak_day", todayKey).apply()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-end digital compass layout
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🧭 بوصلة اتجاه القبلة الذكية",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // The Compass Visual
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(DarkBackground)
                        .border(BorderStroke(2.dp, CardBorder), CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer compass rim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(animatedRotation),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dial marks
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Text("N", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter))
                            Text("S", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter))
                            Text("E", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterEnd))
                            Text("W", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterStart))
                        }

                        // Kaaba icon pointing at local bearing (approx Qibla is 135 degrees from N in some zones)
                        // This icon is attached to the dial so it remains true.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(135f), // Qibla azimuth from North
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text("🕋", fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp))
                        }
                    }

                    // Rotating indicator needle (emerald and steel pointer)
                    Column(
                        modifier = Modifier
                            .fillMaxSize(0.8f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(EmeraldPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(GoldAccent)
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(CardBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isSensorAvailable) "جرى مواءمة البوصلة مع مستشعر الجهاز الحركي" else "محاكاة البوصلة (حرك الشريط لمعايرة القبلة):",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                if (!isSensorAvailable) {
                    Slider(
                        value = manualOffset,
                        onValueChange = { manualOffset = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldAccent,
                            activeTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Text(
                    text = "مؤشر زاوية الانحراف: ${(if (isSensorAvailable) rawAzimuth.toInt() else manualOffset.toInt())}° جهة القبلة",
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Prayers Checklist Track
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "🔥 سلسلة الإنتظام: $prayerStreak يوم",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "🗓️ متابع صلوات اليوم",
                        color = GoldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val prayers = listOf(
                    Triple("الفجر", fajrChecked) { checked: Boolean ->
                        fajrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_fajr", checked).apply()
                        updateStreak()
                    },
                    Triple("الظهر", dhuhrChecked) { checked: Boolean ->
                        dhuhrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_dhuhr", checked).apply()
                        updateStreak()
                    },
                    Triple("العصر", asrChecked) { checked: Boolean ->
                        asrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_asr", checked).apply()
                        updateStreak()
                    },
                    Triple("المغرب", maghribChecked) { checked: Boolean ->
                        maghribChecked = checked
                        prefs.edit().putBoolean("${todayKey}_maghrib", checked).apply()
                        updateStreak()
                    },
                    Triple("العشاء", ishaChecked) { checked: Boolean ->
                        ishaChecked = checked
                        prefs.edit().putBoolean("${todayKey}_isha", checked).apply()
                        updateStreak()
                    }
                )

                prayers.forEach { (name, checked, onCheckedChange) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (checked) EmeraldPrimary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.triggerHaptic()
                                onCheckedChange(!checked)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                viewModel.triggerHaptic()
                                onCheckedChange(it)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                        )

                        Text(
                            text = name,
                            color = if (checked) GoldAccent else TextColorPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 3: 99 BEAUTIFUL NAMES OF ALLAH & MEDITATION
// ---------------------------------------------------------
data class AllahName(val arabic: String, val englishName: String, val meaning: String, val detail: String)

@Composable
fun NamesOfAllahTab(viewModel: EmaniatViewModel) {
    val scrollState = rememberScrollState()
    var selectedName by remember { mutableStateOf<AllahName?>(null) }

    val names = remember {
        listOf(
            AllahName("الرَّحْمَنُ", "Ar-Rahman", "الرحمن بجود كرمه وعطفه الوفير", "الذي تشمل رحمته الكائنات جميعاً ويعم خيره البرايا من غير تمييز."),
            AllahName("الرَّحِيمُ", "Ar-Rahim", "الرحيم بخصوصية عباده المؤمنين", "المنعم برحمته الخاصة على عباده الأتقياء الطائعين الهادين والمستغفرين."),
            AllahName("الْمَلِكُ", "Al-Malik", "الحاكم المطلق ذو السلطان السامي", "الذي يملك الكون كله ويتصرف في ملكوته بحكمة من غير شريك عادل مرشد."),
            AllahName("الْقُدُّوسُ", "Al-Quddus", "المنزه والمقدس عن دنس العيوب", "الطاهر والمنزه عن عيوب ومثالب النقص المترفع بالكمال المطلق الرباني."),
            AllahName("السَّلَامُ", "As-Salam", "ناشر مودة السلام والأمان بفيضه", "الذي سلمت ذاته العظيمة من العيوب وحلت ببركته الأمان والسكينة للنفوس."),
            AllahName("الْمُؤْمِنُ", "Al-Mu'min", "واهب معطيات الأمان والتكامل", "الذي صدّق نفسه ورسله ونشر بأمانه السكينة في أفئدة المرتجين لرحمته."),
            AllahName("الْمُهَيْمِنُ", "Al-Mehymin", "الرقيب والمطلع والحامي للبشر", "الحافظ المطلع الشاهد المسيطر على جميع الخلائق بدقة وإحاطة تامة."),
            AllahName("الْعَزِيزُ", "Al-Aziz", "القدر المنزه بالعز والقوة والمجد", "الغالب الذي لا يهزم الشامخ بالعزة والقوة العظيمة القاهرة التي تشد العروة."),
            AllahName("الْجَبَّارُ", "Al-Jabbar", "الباني وجابر القلوب المنكسرة", "الذي يجبر كسر القلوب ويداوي الضعيف ويصلح بمشيئته أحوال المؤمنين."),
            AllahName("الْمُتَكَبِّرُ", "Al-Mutakabbir", "ذو العلو والكبرياء والجلال المطلق", "المتفرد بصفات العلو والتعالي والجمال، المترفع عن صفات النقص البشري.")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✨ أسماء الله الحسنى ومعانيها الروحانية",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(names) { index, name ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.triggerHaptic()
                            selectedName = name
                        },
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = name.arabic,
                            color = GoldAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name.englishName,
                            color = LightWhite,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name.meaning,
                            color = TextColorSecondary,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Name detailed meditation modal
    if (selectedName != null) {
        val nameData = selectedName!!
        var isMeditating by remember { mutableStateOf(false) }
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isMeditating) 1.25f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        AlertDialog(
            onDismissRequest = { selectedName = null },
            title = {
                Text(
                    text = "شرح وتأمل الاسم الكريم 🕌",
                    color = GoldAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .rotate(if (isMeditating) pulseScale * 15f else 0f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isMeditating) EmeraldPrimary else GoldAccent.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(BorderStroke(1.5.dp, GoldAccent), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nameData.arabic,
                            color = LightWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = nameData.englishName,
                        color = GoldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = nameData.detail,
                        color = TextColorPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulse meditation guide
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMeditating) EmeraldPrimary.copy(alpha = 0.15f)
                                else CardBorder.copy(alpha = 0.1f)
                            )
                            .clickable {
                                isMeditating = !isMeditating
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMeditating) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Meditation State",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMeditating) "جلسة تأمّل حية وغوص في الذكر... (انقر للإيقاف)" else "بدء جلسة تأمّل وقراءة الذكر المستمر",
                            color = LightWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isMeditating) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "تنفس ببطء مع نبض الدائرة الربانية... ردد بقلبك: يا ${nameData.arabic.replace("الـ", "")}",
                            color = EmeraldSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedName = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إغلاق", color = LightWhite)
                }
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ---------------------------------------------------------
// FEATURE 4: CHARITY & ZAKAT CALCULATOR
// ---------------------------------------------------------
@Composable
fun ZakatCalculatorTab(viewModel: EmaniatViewModel) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Inputs
    var cashValue by remember { mutableStateOf("") }
    var goldGrams by remember { mutableStateOf("") }
    var goldCaratPrice by remember { mutableStateOf("240") } // Average price of 1g 24K Gold in SAR/AED
    var silverGrams by remember { mutableStateOf("") }
    var merchandiseValue by remember { mutableStateOf("") }
    var debtsToUser by remember { mutableStateOf("") }
    var debtsFromUser by remember { mutableStateOf("") }

    // Calculated fields
    var totalWealth by remember { mutableStateOf(0f) }
    var goldThresholdValue by remember { mutableStateOf(0f) }
    var zakatDue by remember { mutableStateOf(0f) }
    var isZakatObligatory by remember { mutableStateOf(false) }
    var hasCalculated by remember { mutableStateOf(false) }

    fun calculateZakat() {
        val cash = cashValue.toFloatOrNull() ?: 0f
        val gold = goldGrams.toFloatOrNull() ?: 0f
        val goldPrice = goldCaratPrice.toFloatOrNull() ?: 240f
        val silver = silverGrams.toFloatOrNull() ?: 0f
        val silverPrice = 3.5f // Avg silver price per gram
        val merchandise = merchandiseValue.toFloatOrNull() ?: 0f
        val debtsTo = debtsToUser.toFloatOrNull() ?: 0f
        val debtsFrom = debtsFromUser.toFloatOrNull() ?: 0f

        // Wealth total: Cash + (Gold * goldPrice) + (Silver * silverPrice) + Merchandise + DebtsTo - DebtsFrom
        totalWealth = cash + (gold * goldPrice) + (silver * silverPrice) + merchandise + debtsTo - debtsFrom
        if (totalWealth < 0f) totalWealth = 0f

        // Gold Nisab threshold (85 grams of 24K gold equivalent)
        goldThresholdValue = 85f * goldPrice

        isZakatObligatory = totalWealth >= goldThresholdValue
        zakatDue = if (isZakatObligatory) totalWealth * 0.025f else 0f
        hasCalculated = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💰 حاسبة الزكاة والصدقات الإسلامية",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "املأ قيم أصولك المالية:",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cashValue,
                    onValueChange = { cashValue = it },
                    label = { Text("السيولة النقدية والودائع البنكية") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = goldGrams,
                        onValueChange = { goldGrams = it },
                        label = { Text("الذهب بالجرامات") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = goldCaratPrice,
                        onValueChange = { goldCaratPrice = it },
                        label = { Text("سعر جرام الذهب (24)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = merchandiseValue,
                    onValueChange = { merchandiseValue = it },
                    label = { Text("أصول تجارية وبضائع مخزنة") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = debtsToUser,
                        onValueChange = { debtsToUser = it },
                        label = { Text("ديون مرجوة السداد لك") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = debtsFromUser,
                        onValueChange = { debtsFromUser = it },
                        label = { Text("ديون والتزامات عليك") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.triggerHaptic()
                        calculateZakat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("احسب الزكاة الواجبة 🧮", color = LightWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (hasCalculated) {
            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isZakatObligatory) GoldAccent else EmeraldPrimary.copy(alpha = 0.5f)
                        ),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isZakatObligatory) "وجبت عليك فريضة الزكاة 💰" else "لم تبلغ مالياتك النصاب بعد",
                        color = if (isZakatObligatory) GoldAccent else EmeraldSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "إجمالي رأس المال والأصول الخاضعة: ${totalWealth.toInt()} عملة محلية",
                        color = LightWhite,
                        fontSize = 11.sp
                    )

                    Text(
                        text = "قيمة النصاب المقترنة بـ 85ج ذهب: ${goldThresholdValue.toInt()} عملة محلية",
                        color = TextColorSecondary,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isZakatObligatory) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "القدر الواجب إخراجه (2.5%): ${zakatDue.toInt()} عملة محلية",
                                color = GoldAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Profit monetization callback
                        Button(
                            onClick = {
                                viewModel.triggerHaptic()
                                try {
                                    val adUrl = "https://www.effectivecpmnetwork.com/wykven1z2g?key=8354f640db8eebe8bf7568da45909e36"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                                    context.startActivity(intent)
                                    Toast.makeText(context, "النية الصالحة كافية! جزاكم الله خيراً بدعم تصفح الاعلانات.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "تسجيل نية المساهمة وتوجيه الدعم الفوري 🚀",
                                color = LightWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "ماليتك لم تبلغ حد النصاب، ولكن الصدقات الطوعية تقيك مصارع السوء وتضاعف الأجور.",
                            color = TextColorSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 5: DAILY HADITH SHARE & TEMPLATE MAKER
// ---------------------------------------------------------
data class DailyHadith(val text: String, val narrator: String, val source: String)

@Composable
fun HadithStudioTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    var posterBgColor by remember { mutableStateOf(EmeraldPrimary) }

    val hadiths = remember {
        listOf(
            DailyHadith(
                "«إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى»",
                "عمر بن الخطاب رضي الله عنه",
                "صحيح البخاري ومسلم"
            ),
            DailyHadith(
                "«مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقًا إِلَى الْجَنَّةِ»",
                "أبو هريرة رضي الله عنه",
                "صحيح مسلم"
            ),
            DailyHadith(
                "«الدِّينُ النَّصِيحَةُ» قُلْنَا: لِمَنْ؟ قَالَ: «لِلَّهِ وَلِكِتَابِهِ وَلِرَسُولِهِ وَلأَئِمَّةِ الْمُسْلِمِينَ وَعَامَّتِهِمْ»",
                "تميم الداري رضي الله عنه",
                "صحيح مسلم"
            ),
            DailyHadith(
                "«الْمُسْلِمُ مَنْ سَلَمَ الْمُسْلِمُونَ مِنْ لِسَانِهِ وَيَدِهِ»",
                "عبد الله بن عمرو رضي الله عنهما",
                "صحيح البخاري ومسلم"
            ),
            DailyHadith(
                "«تَبَسُّمُكَ فِي وَجْهِ أَخِيكَ لَكَ صَدَقَةٌ»",
                "أبو ذر الغفاري رضي الله عنه",
                "سنن الترمذي"
            )
        )
    }

    val currentHadith = hadiths[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📜 أحاديث شريفة وبطاقات الحكمة الدعوية",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // The Poster Board
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Background color chips to simulate canvas edit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(EmeraldPrimary, EmeraldMuted, GoldAccent, Color(0xFF1E3A8A), Color(0xFF6B21A8))
                    colors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (posterBgColor == col) LightWhite else Color.Transparent
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    viewModel.triggerHaptic()
                                    posterBgColor = col
                                }
                        )
                    }
                }

                // Styled Quote Canvas Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(posterBgColor.copy(alpha = 0.3f), posterBgColor.copy(alpha = 0.9f))
                            )
                        )
                        .border(BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🕌",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = currentHadith.text,
                            color = LightWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "راوي الحديث: ${currentHadith.narrator}",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "المصدر: ${currentHadith.source}",
                            color = TextColorSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // Swiper Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.triggerHaptic()
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else hadiths.size - 1
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous", tint = GoldAccent)
                    }

                    Text(
                        text = "البطاقة ${currentIndex + 1} من ${hadiths.size}",
                        color = TextColorPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            viewModel.triggerHaptic()
                            currentIndex = if (currentIndex < hadiths.size - 1) currentIndex + 1 else 0
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next", tint = GoldAccent)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.triggerHaptic()
                try {
                    val shareText = "✨ حديث شريف ✨\n\n" +
                            "${currentHadith.text}\n\n" +
                            "راوي الحديث: ${currentHadith.narrator}\n" +
                            "المصدر: ${currentHadith.source}\n\n" +
                            "تمت المشاركة من تطبيق إيمانيات 🕌"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "نشر الحديث الشريف"))
                    Toast.makeText(context, "تم توليد وتنسيق بطاقة الحكمة بنجاح! 🌟", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = LightWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "مشاركة ونشر الحكمة بالزخرفة الإسلامية 🚀",
                color = LightWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Stored stats helpers
private fun getStoredDonationClicks(context: Context): Int {
    val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
    return prefs.getInt("adsterra_clicks_count", 0)
}

private fun saveDonationClicks(context: Context, count: Int) {
    val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
    prefs.edit().putInt("adsterra_clicks_count", count).apply()
}
