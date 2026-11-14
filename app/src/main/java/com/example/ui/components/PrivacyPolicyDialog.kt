package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.*

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val backendBaseUrl = BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")
    val hasWebPolicyUrl = backendBaseUrl.startsWith("https://", ignoreCase = true) &&
        backendBaseUrl.length > "https://".length

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDarkGlass,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = "سياسة الخصوصية",
                tint = GoldAccent,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "سياسة الخصوصية وحماية البيانات",
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrivacyBulletSection(
                    title = "1. تسجيل الدخول والمزامنة السحابية (اختياري)",
                    body = "عند تسجيل الدخول بحساب Google، يُستخدم الاسم والبريد الإلكتروني والمعرف فقط لمزامنة نقاطك وختمتك وأذكارك بأمان عبر Firebase Authentication وCloud Firestore."
                )
                PrivacyBulletSection(
                    title = "2. الميكروفون وتسميع القرآن الكريم",
                    body = "تُطلب صلاحية الميكروفون فقط عند بدء التسميع الصوتي وبموافقتك الصريحة. يُرسل المقطع الصوتي عبر اتصال مشفّر (HTTPS) لتحليل التلاوة وتُحذف التسجيلات المؤقتة مباشرة بعد التحليل دون تخزين على الخادم."
                )
                PrivacyBulletSection(
                    title = "3. الموقع الجغرافي (اختياري)",
                    body = "يُستخدم الموقع الجغرافي محلياً على جهازك لحساب مواقيت الصلاة واتجاه القبلة بدقة، ويمكنك اختيار مدينتك يدوياً في أي وقت دون مشاركة موقعك."
                )
                PrivacyBulletSection(
                    title = "4. حذف الحساب والبيانات",
                    body = "يمكنك حذف حسابك وجميع بياناتك المحفوظة محلياً وسحابياً بشكل نهائي في أي وقت من خلال تبويب (ملفي الشخصي ← حذف الحساب)."
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasWebPolicyUrl) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("$backendBaseUrl/privacy"))
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_web_privacy_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "فتح سياسة الخصوصية على الويب",
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح النسخة الكاملة على الويب 🌐", color = GoldAccent, fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_privacy_dialog_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إغلاق", color = LightWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun PrivacyBulletSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = EmeraldSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            text = body,
            color = TextColorSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
