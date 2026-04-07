# SignOff ProGuard Rules
# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
# Keep Gson
-keep class com.google.gson.** { *; }
