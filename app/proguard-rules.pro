# Add project specific ProGuard rules here.

# Keep annotations
-keepattributes *Annotation*

# Room database classes
-keep class com.example.locationreminder.data.** { *; }
-keepclassmembers class * { @androidx.room.** <methods>; }

# Location services
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Keep field names and method names for Google Play Services
-keepnames class com.google.android.gms.common.internal.safeparcel.** {}

# Encryption provider
-keepclassmembers class * implements java.io.Externalizable {
    void readExternal(java.io.ObjectInputStream);
    void writeExternal(java.io.ObjectOutputStream);
}

# Gson library
-keep class com.google.gson.** { *; }
-dontwarn javax.xml.**
-dontwarn sun.misc.**

# Retrofit (if used)
-keep public class * extends retrofit.Response
-keep public class * extends okhttp.ResponseBody

# Keep member names and class names of anonymous inner classes
-keepnames class ** extends java.lang.Enum { }