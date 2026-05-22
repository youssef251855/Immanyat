package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

data class DuaItem(
    val id: String,
    val text: String,
    val source: String,
    val category: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DuasScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val favoriteDuas by viewModel.favoriteDuas.collectAsState()

    var activeCategory by remember { mutableStateOf("الجميع") }
    val categories = listOf("الجميع", "قرآنية", "نبوية", "فرج وهم", "شفاء وعافية")

    var searchInput by remember { mutableStateOf("") }
    var selectedDuaForSharingCard by remember { mutableStateOf<DuaItem?>(null) }

    // Collection of beautiful Duas
    val duas = remember {
        listOf(
            DuaItem("d1", "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "البقرة - ٢٠١", "قرآنية"),
            DuaItem("d2", "رَبِّ اجْعَلْنِي مُقِيمَ الصَّلَاةِ وَمِن ذُرِّيَّتِي ۚ رَبَّنَا وَتَقَبَّلْ دُعَاءِ", "إبراهيم - ٤٠", "قرآنية"),
            DuaItem("d3", "رَبَّنَا لَا تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا وَهَبْ لَنَا مِن لَّدُنكَ رَحْمَةً ۚ", "آل عمران - ٨", "قرآنية"),
            DuaItem("d4", "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي", "حديث شريف - الترمذي", "نبوية"),
            DuaItem("d5", "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ، وَالْعَجْزِ وَالْكَسَلِ، وَالْبُخْلِ وَالْجُبْنِ...", "حديث شريف - صحيح البخاري", "فرج وهم"),
            DuaItem("d6", "اللهم ربَّ الناس، أذهب الباس، واشفِ أنت الشافي، لا شفاء إلا شفاؤك شفاءً لا يغادر سقماً", "متفق عليه", "شفاء وعافية"),
            DuaItem("d7", "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلَى دِينِكَ", "حديث شريف - الترمذي", "نبوية"),
            DuaItem("d8", "لا إلهَ إلا أنتَ سُبحانَكَ إني كنتُ منَ الظالِمينَ", "الأنبياء - ٨٧", "فرج وهم")
        )
    }

    // Filtered by Search + Category
    val filteredDuas = duas.filter {
        val matchesCategory = activeCategory == "الجميع" || it.category == activeCategory
        val matchesSearch = it.text.contains(searchInput) || it.source.contains(searchInput)
        matchesCategory && matchesSearch
    }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER SCREEN
            Text(
                text = "الأدعية المأثورة",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ادعوا ربكم تضرعاً وخفية - تصفح تصنيفات الضراعة والدعاء",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SEARCH FILTER ADAPTER
            TextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = { Text("ابحث في نصوص الأدعية بالكلمات...", color = TextColorSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .testTag("dua_search_field"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDarkGlass,
                    unfocusedContainerColor = SurfaceDarkGlass,
                    disabledContainerColor = SurfaceDarkGlass,
                    cursorColor = GoldAccent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = LightWhite,
                    unfocusedTextColor = LightWhite
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "بحث",
                        tint = GoldAccent
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CATEGORIES TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { title ->
                    val isSelected = activeCategory == title
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
                                activeCategory = title
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) LightWhite else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DUAS ITEMS LISTING
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDuas) { dua ->
                    val isFavorite = favoriteDuas.contains(dua.id)

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "“${dua.text}”",
                                color = LightWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                lineHeight = 26.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المصدر: ${dua.source}",
                                    color = GoldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .background(EmeraldMuted.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = dua.category,
                                        color = EmeraldSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(12.dp))
                            Spacer(modifier = Modifier.height(4.dp))

                            // Interactive Favorite & Elegant Cards Sharing overlays
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Favorite button
                                IconButton(onClick = { viewModel.toggleFavoriteDua(dua.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "المفضلة",
                                        tint = if (isFavorite) GoldAccent else TextColorSecondary.copy(alpha = 0.4f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Elegant Share card button
                                OutlinedButton(
                                    onClick = { selectedDuaForSharingCard = dua },
                                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = "مشاركة بطاقة",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشاركة كبطاقة فاخرة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // GLOWING POPUP SHARING CARD RENDERING DIALOG (MOCK UP DESIGN IMAGE CAPABILITIES)
        selectedDuaForSharingCard?.let { dua ->
            Dialog(onDismissRequest = { selectedDuaForSharingCard = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    SurfaceDarkGlass,
                                    DarkBackground
                                )
                            )
                        )
                        .border(1.5.dp, GoldAccent, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Title ornamental detail
                        IslamicStarStarIcon(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "إيمانيات • بَطَاقَةُ دُعَاءٍ",
                            color = GoldAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Glassmorphism card body inside dialog
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(EmeraldMuted.copy(alpha = 0.1f))
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "“${dua.text}”",
                                    color = LightWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "المصدر: ${dua.source}",
                                    color = GoldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "تم الحفظ كصورة بنجاح في معرض الصور الخاص بك 🌌",
                            color = EmeraldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { selectedDuaForSharingCard = null },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("إغلاق", color = LightWhite, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { 
                                    // Simulated download sharing
                                },
                                border = BorderStroke(1.dp, GoldAccent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة كصورة الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
