package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmaniatViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isSignUpMode by remember { mutableStateOf(false) }

    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var tempLoggedInName by remember { mutableStateOf("") }

    // Floating notification permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showPermissionDialog = false
        viewModel.setLoginStatus(true, tempLoggedInName.ifEmpty { "عبد الله" })
        if (isGranted) {
            Toast.makeText(context, "تم تفعيل إشعارات الأذان وتذكيرات الذكر بنجاح ✨", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "تم الدخول. يرجى تفعيل الإشعارات لاحقاً للحصول على تنبيهات الأذان بدقة", Toast.LENGTH_LONG).show()
        }
        onLoginSuccess()
    }

    // Function to process login or signup
    val handleAuth = {
        if (emailInput.isEmpty() || passwordInput.isEmpty() || (isSignUpMode && nameInput.isEmpty())) {
            Toast.makeText(context, "يرجى ملء جميع الحقول المطلوبة", Toast.LENGTH_SHORT).show()
        } else if (!Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
            Toast.makeText(context, "يرجى إدخال بريد إلكتروني صحيح", Toast.LENGTH_SHORT).show()
        } else if (passwordInput.length < 6) {
            Toast.makeText(context, "يجب أن تكون كلمة المرور 6 أحرف على الأقل", Toast.LENGTH_SHORT).show()
        } else {
            isLoading = true
            // Simulate networking
            val finalName = if (isSignUpMode) nameInput else emailInput.substringBefore("@")
            tempLoggedInName = finalName
            
            // Wait 1.2 seconds and prompt for notification permission
            android.os.Handler(context.mainLooper).postDelayed({
                isLoading = false
                // Check if permission is already granted
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showPermissionDialog = true
                } else {
                    viewModel.setLoginStatus(true, finalName)
                    onLoginSuccess()
                }
            }, 1200)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBackground,
                            SurfaceDarkGlass.copy(alpha = 0.95f),
                            DarkBackground
                        )
                    )
                )
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            // Main Card container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header decoration
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(EmeraldMuted.copy(alpha = 0.3f))
                        .border(1.5.dp, GoldAccent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mosque,
                        contentDescription = "Mosque Icon",
                        tint = GoldAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "تطبيق إيمانيات",
                    color = GoldAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "رفيقك المسلم في الطاعات والأذكار وأوقات الصلاة",
                    color = TextColorSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                IslamicOrnamentDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .padding(bottom = 14.dp)
                )

                // Sub title tabs (sign in / sign up)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground.copy(alpha = 0.6f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSignUpMode) EmeraldPrimary else Color.Transparent)
                            .clickable {
                                isSignUpMode = false
                                viewModel.triggerHaptic()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تسجيل الدخول",
                            color = if (!isSignUpMode) LightWhite else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSignUpMode) EmeraldPrimary else Color.Transparent)
                            .clickable {
                                isSignUpMode = true
                                viewModel.triggerHaptic()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "إنشاء حساب",
                            color = if (isSignUpMode) LightWhite else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Name field (Sign up only)
                AnimatedVisibility(
                    visible = isSignUpMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("الاسم الكريم", color = TextColorSecondary) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldAccent) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = LightWhite,
                                unfocusedTextColor = LightWhite,
                                focusedContainerColor = DarkBackground.copy(alpha = 0.3f),
                                unfocusedContainerColor = DarkBackground.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Email field
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("البريد الإلكتروني", color = TextColorSecondary) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = GoldAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = LightWhite,
                        unfocusedTextColor = LightWhite,
                        focusedContainerColor = DarkBackground.copy(alpha = 0.3f),
                        unfocusedContainerColor = DarkBackground.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password field
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("كلمة المرور", color = TextColorSecondary) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAccent) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = GoldAccent
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = LightWhite,
                        unfocusedTextColor = LightWhite,
                        focusedContainerColor = DarkBackground.copy(alpha = 0.3f),
                        unfocusedContainerColor = DarkBackground.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons
                Button(
                    onClick = {
                        viewModel.triggerHaptic()
                        handleAuth()
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        disabledContainerColor = EmeraldMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_action_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isSignUpMode) "حفظ وإنشاء الحساب ✨" else "دخول آمن 🔒",
                            color = LightWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Login as Guest
                TextButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        val guestName = "مُحب الطاعة"
                        tempLoggedInName = guestName
                        
                        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }

                        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            showPermissionDialog = true
                        } else {
                            viewModel.setLoginStatus(true, guestName)
                            onLoginSuccess()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "الدخول السريع كـ زائر 🌍",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Modern Islamic Permission Dialog explaining why POST_NOTIFICATIONS is needed for Adhan & Dhikr
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                viewModel.setLoginStatus(true, tempLoggedInName.ifEmpty { "عبد الله" })
                onLoginSuccess()
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            icon = {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(GoldAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "تفعيل إشعارات الأذان 🕋",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "يريد 'إيمانيات' إرسال إشعارات لتنبيهك بوقت الأذان لكل صلاة، وتذكيرك بأذكار الصباح والمساء بدقة عالية لتبقى روحانيتك وثيقة الصلة.",
                    color = TextColorSecondary,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerHaptic()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            showPermissionDialog = false
                            viewModel.setLoginStatus(true, tempLoggedInName)
                            onLoginSuccess()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("منح الإذن والتفعيل ✨", color = LightWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        showPermissionDialog = false
                        viewModel.setLoginStatus(true, tempLoggedInName.ifEmpty { "عبد الله" })
                        onLoginSuccess()
                        Toast.makeText(context, "يمكنك تفعيل الإشعارات لاحقاً دائماً من إعدادات الهاتف", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("تخطي الآن", color = TextColorSecondary)
                }
            },
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(24.dp))
        )
    }
}
