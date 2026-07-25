# kotlinx.serialization: keep generated serializers.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.ghostfoliowidget.app.**$$serializer { *; }
-keepclassmembers class de.ghostfoliowidget.app.** {
    *** Companion;
}
