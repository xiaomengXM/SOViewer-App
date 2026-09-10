# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class kotlin.** { *; }

# 保留 WebView JS 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
