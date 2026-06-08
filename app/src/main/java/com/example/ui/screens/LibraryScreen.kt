package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LibraryArticle
import com.example.data.LibraryCategory
import com.example.data.LibraryData
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    var selectedArticle by remember { mutableStateOf<LibraryArticle?>(null) }
    var readingTextSize by remember { mutableStateOf(16f) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val categories = remember(searchQuery) {
        LibraryData.searchLibrary(searchQuery)
    }

    // Handle back press within the screen
    val onBackClick = {
        viewModel.triggerHaptic()
        if (selectedArticle != null) {
            selectedArticle = null
        } else if (selectedCategory != null) {
            selectedCategory = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER SCREEN BAR (with Back support if in sub-views)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedCategory != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldMuted.copy(alpha = 0.2f))
                            .testTag("library_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع للخلف",
                            tint = GoldAccent
                        )
                    }
                } else {
                    // Decorative Badge matching the image pattern
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldSecondary.copy(alpha = 0.2f))
                            .border(1.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "المكتبة الإسلامية",
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Current Context Label
                Text(
                    text = when {
                        selectedArticle != null -> "تصفح المقال"
                        selectedCategory != null -> selectedCategory!!.name
                        else -> "المكتبة والقصص"
                    },
                    color = LightWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
            Spacer(modifier = Modifier.height(12.dp))

            // RENDER RELEVANT VIEW
            AnimatedContent(
                targetState = Triple(selectedCategory, selectedArticle, searchQuery),
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
                },
                modifier = Modifier.weight(1f),
                label = "LibraryContentAnimation"
            ) { state ->
                val (cat, art, query) = state

                when {
                    art != null -> {
                        // 1. ARTICLE DETAIL VIEW
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 80.dp) // space for floating bottom music / padding
                        ) {
                            Text(
                                text = art.title,
                                color = GoldAccent,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // Source Label
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (readingTextSize > 12f) readingTextSize -= 2f
                                            viewModel.triggerHaptic()
                                        },
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(EmeraldMuted.copy(alpha = 0.15f))
                                    ) {
                                        Text("أ-", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            if (readingTextSize < 28f) readingTextSize += 2f
                                            viewModel.triggerHaptic()
                                        },
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(EmeraldMuted.copy(alpha = 0.15f))
                                    ) {
                                        Text("أ+", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Text(
                                    text = "المصدر: ${art.source}",
                                    color = TextColorSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Solid Card styled like scroll paper background
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceDarkGlass)
                                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                    .padding(18.dp)
                            ) {
                                Text(
                                    text = art.content,
                                    color = TextColorPrimary,
                                    fontSize = readingTextSize.sp,
                                    lineHeight = (readingTextSize * 1.55f).sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Justify,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Interactive Controls: Copy & Share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(art.title + "\n\n" + art.content))
                                        Toast.makeText(context, "تم نسخ المقال إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                        viewModel.triggerHaptic()
                                    },
                                    modifier = Modifier.weight(1f).testTag("copy_article_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary.copy(alpha = 0.25f)),
                                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("نسخ الفائدة", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.triggerHaptic()
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_SUBJECT, art.title)
                                            putExtra(Intent.EXTRA_TEXT, "${art.title}\n\n${art.content}")
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "نشر وتطبيق الفائدة")
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.weight(1f).testTag("share_article_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = LightWhite, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("نشر الفائدة", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    cat != null -> {
                        // 2. ARTICLES DE CATEGORY LIST VIEW
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                Text(
                                    text = "اختر العبرة أو الدرس لتصفحه بتأن وتدبر:",
                                    color = TextColorSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    textAlign = TextAlign.Right
                                )
                            }

                            items(cat.articles) { article ->
                                GlassCardInteractive(
                                    onClick = {
                                        selectedArticle = article
                                        viewModel.triggerHaptic()
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("article_card_${article.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "تصفح",
                                            tint = GoldAccent
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = article.title,
                                                color = LightWhite,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Right
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = article.content,
                                                color = TextColorSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                textAlign = TextAlign.Right
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Beautiful Star/Badge for story icon
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(EmeraldSecondary.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MenuBook,
                                                contentDescription = null,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // 3. MAIN CATEGORIES GRID GRID
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search bar field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("library_search_input"),
                                placeholder = {
                                    Text(
                                        text = "البحث عن القصص، العبر، أو الأبواب...",
                                        color = TextColorSecondary.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = GoldAccent
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "مسح البحث",
                                                tint = TextColorSecondary
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = CardBorder,
                                    focusedContainerColor = SurfaceDarkGlass,
                                    unfocusedContainerColor = SurfaceDarkGlass,
                                    focusedTextColor = LightWhite,
                                    unfocusedTextColor = LightWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (categories.isEmpty()) {
                                // Clean, elegant empty results placeholder
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = GoldAccent.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "مرحى عذراً، لم نجد نتائج لـ \"$searchQuery\"",
                                            color = TextColorSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "جرّب كتابة كلمات مفتاحية أخرى مثل: (أحد، الاستغفار، جبريل)",
                                            color = TextColorSecondary.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                // Grid of categories
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(categories) { category ->
                                        GlassCardInteractive(
                                            onClick = {
                                                selectedCategory = category
                                                viewModel.triggerHaptic()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("category_card_${category.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Left Arrow pointing to interior
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = "عرض المحتوى",
                                                    tint = GoldAccent
                                                )

                                                // Category Name on correct RTL alignment
                                                Column(
                                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = category.name,
                                                        color = LightWhite,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Right
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "${category.count} موضوعاً مفصلاً",
                                                        color = GoldAccent,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                // Right Book Icon representing category
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(EmeraldSecondary.copy(alpha = 0.2f))
                                                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = when(category.id) {
                                                            "allah" -> Icons.Default.Favorite
                                                            "angels" -> Icons.Default.Adjust
                                                            "miracle_quran" -> Icons.Default.Language
                                                            "hadith_qudsi" -> Icons.Default.FormatQuote
                                                            "prophet_features" -> Icons.Default.AutoAwesome
                                                            "prophet_battles" -> Icons.Default.Security
                                                            "prophet_wills" -> Icons.Default.MarkChatRead
                                                            "quran_stories" -> Icons.Default.LibraryBooks
                                                            "prophets_stories" -> Icons.Default.MenuBook
                                                            "companions_stories" -> Icons.Default.Person
                                                            "female_companions_stories" -> Icons.Default.Face
                                                            "tabiun_stories" -> Icons.Default.Group
                                                            "lessons_stories" -> Icons.Default.Lightbulb
                                                            "leaders" -> Icons.Default.MilitaryTech
                                                            "abrogation" -> Icons.Default.ChangeCircle
                                                            "major_signs" -> Icons.Default.AccessTime
                                                            "gate_of_knowledge" -> Icons.Default.School
                                                            "prophet_medicine" -> Icons.Default.Healing
                                                            else -> Icons.Default.Book
                                                        },
                                                        contentDescription = null,
                                                        tint = GoldAccent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
