# kotlinx.serialization: keep generated serializers.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.poltergeld.**$$serializer { *; }
-keepclassmembers class app.poltergeld.** {
    *** Companion;
}
