# Keep Room entities & DAOs
-keep class com.keepsakeai.app.data.local.** { *; }

# Keep AI response models
-keep class com.keepsakeai.app.ai.** { *; }

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
