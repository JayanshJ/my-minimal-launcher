# ── Minimal Launcher ProGuard Rules ─────────────────────────────────────────

# Keep all launcher classes (activities, adapters, models, prefs)
-keep class com.minimal.launcher.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── AndroidX / ViewBinding ────────────────────────────────────────────────────
-keep class androidx.viewbinding.** { *; }
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(android.view.LayoutInflater);
    public static ** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static ** bind(android.view.View);
}

# RecyclerView DiffUtil
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$Adapter {
    <methods>;
}

# ── Android framework ─────────────────────────────────────────────────────────
# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep enums (used by SharedPreferences string comparisons)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R class entries (needed by ViewBinding field lookups)
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ── Debugging ─────────────────────────────────────────────────────────────────
# Preserve line numbers in stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
