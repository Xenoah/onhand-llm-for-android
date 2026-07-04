# MediaPipe (JNI 経由で参照されるため難読化しない)
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.onhand.llm.** {
    *** Companion;
}
-keepclasseswithmembers class com.onhand.llm.** {
    kotlinx.serialization.KSerializer serializer(...);
}
