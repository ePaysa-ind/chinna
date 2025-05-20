# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# OkHttp and Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Google Gemini AI
-keep class com.google.ai.client.generativeai.** { *; }
-keepclassmembers class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# App models
-keep class com.example.chinna.model.** { *; }
-keep class com.example.chinna.data.local.** { *; }
-keep class com.example.chinna.data.remote.** { *; }
-keep class com.example.chinna.data.repository.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-dontwarn com.bumptech.glide.**

# ViewBinding
-keep class com.example.chinna.databinding.** { *; }

# Navigation Component
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# Android Architecture Components
-keep class androidx.lifecycle.** { *; }
-keep class androidx.arch.core.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.arch.core.**