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

# Retrofit reads these through reflection: generic types, annotations, and the API interfaces.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep interface com.githunt.android.data.**.*Api { *; }

# The app's @Serializable models (JSON field names must survive).
-keep @kotlinx.serialization.Serializable class com.githunt.android.** { *; }

# Optional / compile-time-only classes that some libraries mention but the app never loads.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.noties.markwon.**
