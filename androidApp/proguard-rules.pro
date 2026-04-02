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

-keep,includedescriptorclasses class com.alfanar.atc.**$$serializer { *; }
-keepclassmembers class com.alfanar.atc.** {
    *** Companion;
}
-keepclasseswithmembers class com.alfanar.atc.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Koin
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }

# Keep data classes and models
-keep class com.alfanar.atc.**.dto.** { *; }
-keep class com.alfanar.atc.**.model.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Napier logging - strip debug logs in release
-assumenosideeffects class io.github.aakira.napier.Napier {
    public static void d(...);
    public static void v(...);
}
