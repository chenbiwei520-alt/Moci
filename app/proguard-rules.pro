# PDFBox reads fonts and encodings through reflection.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.tom_roush.harmony.**

# Room instantiates generated database classes reflectively. The bundled Room
# 2.2.5 rule only keeps the class, so R8 strips the no-arg constructor.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Enum constant names are part of the stored progress format.
-keep enum com.cbw21.moci.data.AnswerStatus { *; }

# Keep generated serializers so book and progress JSON stays readable forever.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.cbw21.moci.**$$serializer { *; }
-keepclassmembers class com.cbw21.moci.** {
    *** Companion;
}
-keepclasseswithmembers class com.cbw21.moci.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Optional PDF encryption/signature backends.
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
