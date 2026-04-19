# Add project-specific ProGuard rules here.

# kotlinx.serialization - keep @Serializable classes and their serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.vibepad.keyboard.**$$serializer { *; }
-keepclassmembers class com.vibepad.keyboard.** {
    *** Companion;
}
-keepclasseswithmembers class com.vibepad.keyboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
