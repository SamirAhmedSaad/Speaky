# Add project specific ProGuard rules here.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serialization for app classes
-keep,includedescriptorclasses class com.speakmind.app.**$$serializer { *; }
-keepclassmembers class com.speakmind.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.speakmind.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes and models
-keep class com.speakmind.app.**.dto.** { *; }
-keep class com.speakmind.app.**.model.** { *; }
-keep class com.speakmind.app.**.data.** { *; }

# Keep Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Koin
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }

# Keep Firebase & Crashlytics
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-keep class com.crashlytics.** { *; }
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Keep SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keepclassmembers class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
-keep class com.speakmind.app.db.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }
-keepclassmembers class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep Room generated implementations (used by WorkManager internally)
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep JNI — LLamaAndroid native methods must not be renamed
-keep class android.llama.cpp.LLamaAndroid { *; }
-keepclasseswithmembernames class android.llama.cpp.LLamaAndroid {
    native <methods>;
}

# Keep androidx.startup initializers
-keep class * extends androidx.startup.Initializer { *; }
-keepclassmembers class androidx.startup.** { *; }
-dontwarn androidx.startup.**

# Keep Compose Navigation @Serializable destinations (used via reflection)
-keep @kotlinx.serialization.Serializable class com.speakmind.app.navigation.** { *; }

# Keep Google Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# Keep Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Napier logging - strip debug logs in release
-assumenosideeffects class io.github.aakira.napier.Napier {
    public static void d(...);
    public static void v(...);
}

# Compose Multiplatform resources — generated accessor classes must not be removed or renamed,
# otherwise the runtime asset path lookup fails and fonts/images throw "asset not found"
-keep class speaky.composeapp.generated.resources.** { *; }
-keepclassmembers class speaky.composeapp.generated.resources.** { *; }
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn org.jetbrains.compose.resources.**
