# kotlinx.serialization 通过伴生对象反射查找生成的序列化器，
# R8 会把这些符号当成无用代码删掉，导致运行时找不到序列化器。
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.pulse.tts.**$$serializer { *; }

-keepclassmembers class com.pulse.tts.** {
    *** Companion;
}

-keepclasseswithmembers class com.pulse.tts.** {
    kotlinx.serialization.KSerializer serializer(...);
}
