# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
# Keep the Room database classes
-keep class com.example.locationreminder.data.** { *; }