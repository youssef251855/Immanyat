package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AuthHelper
import com.example.data.AuthResult
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmaniatViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWebFallbackOption by remember { mutableStateOf(false) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var tempLoggedInName by remember { mutableStateOf("") }
    var tempLoggedInEmail by remember { mutableStateOf("") }
    var tempLoggedInUid by remember { mutableStateOf("") }

    // Floating notification permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showPermissionDialog = false
        viewModel.setLoginStatus(
            loggedIn = true,
            name = tempLoggedInName.ifEmpty { "عبد الله" },
            email = tempLoggedInEmail,
            uid = tempLoggedInUid
        )
        if (isGranted) {
            Toast.makeText(context, "تم تفعيل إشعارات الأذان وتذكيرات الذكر بنجاح ✨", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "تم الدخول بنجاح ✨", Toast.LENGTH_SHORT).show()
        }
        onLoginSuccess()
    }

    val finalizeLogin = { name: String, email: String, uid: String ->
        tempLoggedInName = name
        tempLoggedInEmail = email
        tempLoggedInUid = uid

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showPermissionDialog = true
        } else {
            viewModel.setLoginStatus(true, name, email, uid)
            onLoginSuccess()
        }
    }

    // Attempt silent auto sign-in on startup if already authorized
    LaunchedEffect(Unit) {
        val autoResult = AuthHelper.attemptAutoSignIn(context)
        if (autoResult is AuthResult.Success) {
            finalizeLogin(autoResult.displayName, autoResult.email, autoResult.user?.uid ?: "")
        }
    }

    // Interactive Google Sign-In handling
    val handleGoogleSignIn = {
        if (!isGoogleLoading) {
            errorMessage = null
            showWebFallbackOption = false
            isGoogleLoading = true
            coroutineScope.launch {
                val result = AuthHelper.signInWithGoogle(context)
                isGoogleLoading = false
                when (result) {
                    is AuthResult.Success -> {
                        finalizeLogin(result.displayName, result.email, result.user?.uid ?: "")
                    }
                    is AuthResult.Cancelled -> {
                        showWebFallbackOption = true
                        errorMessage = "تم إلغاء نافذة اختيار الحساب أو تعذر التحقق من شهادة التطبيق على الجهاز. يمكنك إعادة المحاولة أو المتابعة عبر المتصفح."
                    }
                    is AuthResult.Error -> {
                        showWebFallbackOption = true
                        errorMessage = result.message
                    }
                }
            }
        }
    }

    val handleWebGoogleSignIn = {
        if (!isGoogleLoading) {
            errorMessage = null
            isGoogleLoading = true
            coroutineScope.launch {
                val result = AuthHelper.signInWithGoogleWebFallback(context)
                isGoogleLoading = false
                when (result) {
                    is AuthResult.Success -> {
                        showWebFallbackOption = false
                        finalizeLogin(result.displayName, result.email, result.user?.uid ?: "")
                    }
                    is AuthResult.Cancelled -> {
                        errorMessage = "تم إلغاء تسجيل الدخول عبر المتصفح."
                    }
                    is AuthResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(20.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(vertical = 28.dp, horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header decoration
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(EmeraldMuted.copy(alpha = 0.3f))
                        .border(1.5.dp, GoldAccent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mosque,
                        contentDescription = "شعار تطبيق إيمانيات",
                        tint = GoldAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "تطبيق إيمانيات",
                    color = GoldAccent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "سجّل دخولك بحساب Google لحفظ إنجازاتك وختماتك وتسبيحاتك سحابياً ومزامنتها عبر أجهزتك ☁️",
                    color = TextColorSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )

                IslamicOrnamentDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .padding(bottom = 16.dp)
                )

                // Error Message if any
                if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.12f))
                            .border(0.8.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF8A8A),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showWebFallbackOption) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerHaptic()
                                    handleWebGoogleSignIn()
                                },
                                enabled = !isGoogleLoading,
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("google_web_fallback_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "تسجيل الدخول عبر المتصفح",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "المتابعة بحساب Google عبر المتصفح 🌐",
                                    color = GoldAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Google Sign In Button
                Button(
                    onClick = {
                        viewModel.triggerHaptic()
                        handleGoogleSignIn()
                    },
                    enabled = !isGoogleLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .testTag("google_sign_in_button")
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "المتابعة بحساب Google",
                                color = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cloud benefits pill
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkBackground.copy(alpha = 0.45f))
                        .border(0.5.dp, CardBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("نسخ احتياطي سحابي فوري للختمة والسبحة", color = LightWhite, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مصادقة آمنة ومشفرة عبر حساب Google", color = LightWhite, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Local-only guest mode (no Firestore calls)
                TextButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        finalizeLogin("مُحب الطاعة", "", "")
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guest_local_button")
                ) {
                    Text(
                        text = "المتابعة بدون حساب (حفظ محلي على الجهاز فقط)",
                        color = TextColorSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        showPrivacyDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_privacy_policy_button")
                ) {
                    Text(
                        text = "سياسة الخصوصية وحماية البيانات 🔒",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    // Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                viewModel.setLoginStatus(true, tempLoggedInName.ifEmpty { "عبد الله" }, tempLoggedInEmail, tempLoggedInUid)
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
                    text = "يريد 'إيمانيات' إرسال إشعارات لتنبيهك بوقت الأذان لكل صلاة وتذكيرك بأذكار الصباح والمساء بدقة لتبقى روحانيتك وثيقة الصلة.",
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
                            viewModel.setLoginStatus(true, tempLoggedInName, tempLoggedInEmail, tempLoggedInUid)
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
                        viewModel.setLoginStatus(true, tempLoggedInName.ifEmpty { "عبد الله" }, tempLoggedInEmail, tempLoggedInUid)
                        onLoginSuccess()
                    }
                ) {
                    Text("تخطي الآن", color = TextColorSecondary)
                }
            },
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(24.dp))
        )
    }
}
