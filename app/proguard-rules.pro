-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao <methods>;
}
-dontwarn org.json.**
