# Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# Firebase Auth & Firestore
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.example.data.UserCloudProfile { *; }
