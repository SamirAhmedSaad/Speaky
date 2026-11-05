# R8 rules for Speaky.
# Keep these narrow: Compose, Ktor, Koin, coroutines, Firebase, WorkManager, Room, SQLDelight,
# Credential Manager, Unity LevelPlay and Supabase ship their own consumer rules. Broad
# "-keep class lib.** { *; }" rules stop R8 from shrinking/optimizing and Google Play flags
# the app when its optimization score drops below 25%.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# Readable Crashlytics stack traces
-keepattributes SourceFile, LineNumberTable
-keep public class * extends java.lang.Exception

# ---------- kotlinx.serialization (all @Serializable classes: DTOs, nav destinations) ----------
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { <fields>; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Type-safe Navigation destinations are looked up by class name
-keep @kotlinx.serialization.Serializable class com.speakmind.app.navigation.** { *; }

# ---------- On-device AI (do not change) ----------
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
# JNI — LLamaAndroid native methods must not be renamed
-keep class android.llama.cpp.LLamaAndroid { *; }
-keep class android.llama.cpp.LLamaAndroid$** { *; }
-keepclasseswithmembernames class android.llama.cpp.LLamaAndroid {
    native <methods>;
}

# ---------- Compose Multiplatform resources ----------
# Generated accessors must not be removed or renamed, or fonts/images throw "asset not found".
-keep class speaky.composeapp.generated.resources.** { *; }
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn org.jetbrains.compose.resources.**

# ---------- Firebase ----------
# Components (Messaging, Crashlytics, Analytics, Installations) are created by reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
# Messaging service is started by the system
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService

# ---------- Credential Manager + Sign in with Google ----------
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# ---------- Ads (Unity LevelPlay) — WebView bridges ----------
-keepattributes JavascriptInterface
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# ---------- Warnings from optional dependencies ----------
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn com.unity3d.**
-dontwarn com.ironsource.**
-dontwarn kotlinx.datetime.**
-dontwarn io.github.jan.supabase.**

# ---------- Napier: strip debug/verbose logs in release ----------
-assumenosideeffects class io.github.aakira.napier.Napier {
    public static void d(...);
    public static void v(...);
}

# Agora voice SDK: its native code looks classes up by name (JNI), so R8 must keep them.
-keep class io.agora.** { *; }
-dontwarn io.agora.**
