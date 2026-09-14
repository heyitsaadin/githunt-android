# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class com.githunt.android.**$$serializer { *; }
-keepclassmembers class com.githunt.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.githunt.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
