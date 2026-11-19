# Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# Firebase Auth & Firestore & Serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.example.data.UserCloudProfile { *; }

# Moshi & Retrofit JSON Models (preserve fields & generated adapters)
-keep class com.example.api.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Offline Whisper.cpp JNI Native Bindings (critical: native C++ calls depend on exact signatures)
-keep class dev.ffmpegkit.whisper.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Room Database Entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# WorkManager Workers & BroadcastReceivers & Widgets
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.example.service.** { *; }
-keep class com.example.widget.** { *; }

# Adhan Astronomical Library
-keep class com.batoulapps.adhan.** { *; }

# Strip debug/verbose/info Logcat calls in release APK so URLs or runtime tokens never leak via logcat
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
