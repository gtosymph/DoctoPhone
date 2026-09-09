-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kmt.healthanalyzer.**$$serializer { *; }
-keepclassmembers class com.kmt.healthanalyzer.** {
    *** Companion;
}
-keepclasseswithmembers class com.kmt.healthanalyzer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# DataStore Preferences sérialise ses réglages avec un protobuf-lite repackagé sous
# androidx.datastore.preferences.protobuf. Ce protobuf retrouve ses champs par leur NOM,
# par réflexion, au moment d'écrire. R8 renomme `value_` en `f`, la recherche échoue, et
# l'app plante à la première écriture d'un réglage :
#
#   RuntimeException: Field value_ for PreferencesProto$Value not found.
#
# Le défaut n'existe qu'en release, puisque le build debug n'est pas minifié. Il touche
# TOUS les réglages, pas un fournisseur en particulier.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
