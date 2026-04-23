# Keep GSON serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class org.nemotech.rsc.model.player.SaveFile { *; }
-keep class org.nemotech.rsc.model.player.Cache { *; }
-keep class org.nemotech.rsc.external.definition.** { *; }
-keep class org.nemotech.rsc.external.location.** { *; }
