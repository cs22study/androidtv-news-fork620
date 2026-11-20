# Proguard / R8 混淆保護規則 (確保 Chaquopy, ACRA, ExoPlayer, LocalWebServer 完整性)

-keep class io.github.anenasa.news.** { *; }
-keepclassmembers class io.github.anenasa.news.** { *; }

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keep class com.chaquo.python.** { *; }
-keep class org.acra.** { *; }
-keep class androidx.media3.** { *; }

-dontwarn com.chaquo.python.**
-dontwarn org.acra.**
