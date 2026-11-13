# MediaPipe — keep all task model classes
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# UCrop
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# Glance AppWidget
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# javax.lang.model — referenced by annotation processors (AutoValue, Dagger) but
# not present at runtime on Android. Safe to suppress.
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
