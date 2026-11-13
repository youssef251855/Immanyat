package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    val defaultGoldGramPrice: Double
)

val CURRENCIES = listOf(
    CurrencyInfo("SAR", "ر.س", "ريال سعودي", 330.0),
    CurrencyInfo("EGP", "ج.م", "جنيه مصري", 4200.0),
    CurrencyInfo("USD", "$", "دولار أمريكي", 88.0),
    CurrencyInfo("AED", "د.إ", "درهم إماراتي", 325.0),
    CurrencyInfo("KWD", "د.ك", "دينار كويتي", 27.0),
    CurrencyInfo("QAR", "ر.ق", "ريال قطري", 320.0),
    CurrencyInfo("JOD", "د.أ", "دينار أردني", 62.0),
    CurrencyInfo("MAD", "د.م", "درهم مغربي", 880.0),
    CurrencyInfo("DZD", "د.ج", "دينار جزائري", 11800.0),
    CurrencyInfo("TRY", "₺", "ليرة تركية", 2900.0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatCalculatorScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedCurrency by remember { mutableStateOf(CURRENCIES[0]) }
    var goldPricePerGram by remember { mutableStateOf(selectedCurrency.defaultGoldGramPrice.toString()) }
    var silverPricePerGram by remember { mutableStateOf((selectedCurrency.defaultGoldGramPrice * 0.013).toInt().toString()) }

    // Input Assets
    var gold24Grams by remember { mutableStateOf("") }
    var gold21Grams by remember { mutableStateOf("") }
    var gold18Grams by remember { mutableStateOf("") }
    var silverGrams by remember { mutableStateOf("") }

    var cashSavings by remember { mutableStateOf("") }
    var bankBalance by remember { mutableStateOf("") }
    var businessGoods by remember { mutableStateOf("") }
    var debtsToYou by remember { mutableStateOf("") }
    var debtsYouOwe by remember { mutableStateOf("") }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    // Calculations
    val goldPrice = goldPricePerGram.toDoubleOrNull() ?: selectedCurrency.defaultGoldGramPrice
    val silverPrice = silverPricePerGram.toDoubleOrNull() ?: (goldPrice * 0.013)

    val g24 = gold24Grams.toDoubleOrNull() ?: 0.0
    val g21 = gold21Grams.toDoubleOrNull() ?: 0.0
    val g18 = gold18Grams.toDoubleOrNull() ?: 0.0
    val silver = silverGrams.toDoubleOrNull() ?: 0.0

    // Converted to 24K equivalent: 21K is 21/24, 18K is 18/24
    val total24kEquivalentGrams = g24 + (g21 * 21.0 / 24.0) + (g18 * 18.0 / 24.0)
    val goldValue = total24kEquivalentGrams * goldPrice
    val silverValue = silver * silverPrice

    val cashVal = cashSavings.toDoubleOrNull() ?: 0.0
    val bankVal = bankBalance.toDoubleOrNull() ?: 0.0
    val businessVal = businessGoods.toDoubleOrNull() ?: 0.0
    val debtsToYouVal = debtsToYou.toDoubleOrNull() ?: 0.0
    val debtsYouOweVal = debtsYouOwe.toDoubleOrNull() ?: 0.0

    val grossAssets = goldValue + silverValue + cashVal + bankVal + businessVal + debtsToYouVal
    val netZakatableWealth = maxOf(0.0, grossAssets - debtsYouOweVal)

    // Nisab = 85 grams of 24k Gold
    val nisabValue = 85.0 * goldPrice
    val isNisabReached = netZakatableWealth >= nisabValue

    // Zakat amount is 2.5% (1/40th)
    val zakatDue = if (isNisabReached) netZakatableWealth * 0.025 else 0.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("zakat_calculator_screen")
    ) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Top Bar
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
                        text = "حاسبة الزكاة الشرعية 💰",
                        color = GoldAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "وفق الضوابط الفقهية والنصاب الشرعي",
                        color = TextColorSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { showInfoDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "شروط الزكاة",
                        tint = GoldAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quran Verse Reminder Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "﴿ خُذْ مِنْ أَمْوَالِهِمْ صَدَقَةً تُطَهِّرُهُمْ وَتُزَكِّيهِم بِهَا ﴾",
                        color = GoldAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سورة التوبة - الآية ١٠٣",
                        color = EmeraldSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BIG RESULT SUMMARY CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            if (isNisabReached) listOf(Color(0xFF1E3A2F), Color(0xFF0F1E19))
                            else listOf(Color(0xFF263238), Color(0xFF101820))
                        )
                    )
                    .border(
                        1.5.dp,
                        if (isNisabReached) GoldAccent else CardBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isNisabReached) "الزكاة الواجبة إخراجها (٢.٥٪) ✨" else "لم يبلغ النصاب بعد ⏳",
                            color = if (isNisabReached) GoldAccent else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // Currency Selector Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldAccent.copy(alpha = 0.15f))
                                .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { showCurrencyPicker = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedCurrency.name} (${selectedCurrency.symbol})",
                                    color = GoldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Big Due Zakat Value
                    Text(
                        text = String.format(Locale.US, "%,.2f", zakatDue),
                        color = if (isNisabReached) Color(0xFF4CAF50) else LightWhite,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = selectedCurrency.symbol,
                        color = GoldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(14.dp))

                    // Breakdown row: Total Zakatable vs Nisab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "إجمالي الوعاء الزكوي",
                                color = TextColorSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,.0f", netZakatableWealth)} ${selectedCurrency.symbol}",
                                color = LightWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "قيمة النصاب (٨٥غ ذهب)",
                                color = TextColorSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,.0f", nisabValue)} ${selectedCurrency.symbol}",
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isNisabReached) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val shareText = "حساب زكاتي عبر تطبيق إيمانيات:\nإجمالي الوعاء: ${String.format(Locale.US, "%,.2f", netZakatableWealth)} ${selectedCurrency.symbol}\nمقدار الزكاة الواجبة (2.5%): ${String.format(Locale.US, "%,.2f", zakatDue)} ${selectedCurrency.symbol}\nنسأل الله القبول والبركة."
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة حساب الزكاة"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مشاركة تفاصيل حساب الزكاة 📤",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: PRICES SETTINGS
            Text(
                text = "١. أسعار الذهب والفضة الحالية",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).padding(bottom = 6.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = goldPricePerGram,
                        onValueChange = { goldPricePerGram = it },
                        label = { Text("سعر غرام الذهب عيار 24", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = silverPricePerGram,
                        onValueChange = { silverPricePerGram = it },
                        label = { Text("سعر غرام الفضة", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: GOLD & SILVER ASSETS
            Text(
                text = "٢. الذهب والفضة والمدخرات الثمينة",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).padding(bottom = 6.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = gold24Grams,
                            onValueChange = { gold24Grams = it },
                            label = { Text("ذهب عيار 24 (غ)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gold21Grams,
                            onValueChange = { gold21Grams = it },
                            label = { Text("ذهب عيار 21 (غ)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gold18Grams,
                            onValueChange = { gold18Grams = it },
                            label = { Text("ذهب عيار 18 (غ)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = silverGrams,
                        onValueChange = { silverGrams = it },
                        label = { Text("وزن الفضة الخالصة بالغرام", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 3: CASH, SAVINGS & COMMERCE
            Text(
                text = "٣. السيولة النقدية، الحسابات البنكية وعروض التجارة",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).padding(bottom = 6.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cashSavings,
                        onValueChange = { cashSavings = it },
                        label = { Text("السيولة النقدية والعملات الأجنبية المحولة", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bankBalance,
                        onValueChange = { bankBalance = it },
                        label = { Text("الأرصدة والودائع البنكية والشهادات الادخارية", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = businessGoods,
                        onValueChange = { businessGoods = it },
                        label = { Text("بضائع وعروض التجارة والأسهم المعدة للبيع (بسعر السوق)", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 4: DEBTS & LIABILITIES
            Text(
                text = "٤. الديون والالتزامات المالية",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).padding(bottom = 6.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = debtsToYou,
                        onValueChange = { debtsToYou = it },
                        label = { Text("ديون لك مرجوة السداد عند الغير (+ تُضاف)", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = GoldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = debtsYouOwe,
                        onValueChange = { debtsYouOwe = it },
                        label = { Text("ديون والتزامات حالة عليك للغير (- تُخصم)", fontSize = 11.sp) },
                        suffix = { Text(selectedCurrency.symbol, fontSize = 11.sp, color = Color(0xFFEF5350)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350),
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = LightWhite,
                            unfocusedTextColor = LightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // RESET BUTTON
            TextButton(
                onClick = {
                    gold24Grams = ""
                    gold21Grams = ""
                    gold18Grams = ""
                    silverGrams = ""
                    cashSavings = ""
                    bankBalance = ""
                    businessGoods = ""
                    debtsToYou = ""
                    debtsYouOwe = ""
                    viewModel.triggerHaptic()
                    Toast.makeText(context, "تمت إعادة تعيين الحقول", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TextColorSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "إعادة تعيين جميع الحقول", color = TextColorSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        // INFO DIALOG
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                containerColor = DarkBackgroundElevated,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "شروط وجوب الزكاة في الإسلام 📜",
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
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "١. بلوغ النصاب: وهو ما يعادل قيمة 85 غراماً من الذهب الخالص (عيار 24) أو 595 غراماً من الفضة.",
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "٢. حولان الحول: أن يمر عام هجري كامل (أو ميلادي مع إضافة 2.577%) على امتلاك المال وهو بالغ للنصاب.",
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "٣. الملك التام: أن يكون المال مملوكاً للشخص ملكاً مستقراً وحراً في التصرف فيه.",
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "٤. الفضل عن الحاجات الأصلية: أن يكون المال زائداً عن النفقات الأساسية كالطعام والمسكن والملبس والدواء.",
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "٥. نسبة الزكاة: ربع العشر (2.5%) من إجمالي المال الصافي الخاضع للزكاة.",
                            color = EmeraldSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "فهمت جزاكم الله خيراً", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // CURRENCY PICKER DIALOG
        if (showCurrencyPicker) {
            AlertDialog(
                onDismissRequest = { showCurrencyPicker = false },
                containerColor = DarkBackgroundElevated,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "اختر عملة الحساب 💱",
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
                        CURRENCIES.forEach { curr ->
                            val isSelected = curr.code == selectedCurrency.code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EmeraldMuted.copy(alpha = 0.3f) else Color.Transparent)
                                    .clickable {
                                        selectedCurrency = curr
                                        goldPricePerGram = curr.defaultGoldGramPrice.toString()
                                        silverPricePerGram = (curr.defaultGoldGramPrice * 0.013).toInt().toString()
                                        showCurrencyPicker = false
                                        viewModel.triggerHaptic()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = curr.name,
                                        color = if (isSelected) GoldAccent else LightWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${curr.symbol} (${curr.code})",
                                        color = TextColorSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isSelected) {
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
                    TextButton(onClick = { showCurrencyPicker = false }) {
                        Text(text = "إلغاء", color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
