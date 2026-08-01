# Add project specific ProGuard rules here.
# Keep rules for kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.queststack.**$$serializer { *; }
-keepclassmembers class com.queststack.** {
    *** Companion;
}
-keepclasseswithmembers class com.queststack.** {
    kotlinx.serialization.KSerializer serializer(...);
}
