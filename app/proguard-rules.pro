# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.maslarski.sudoku.**$$serializer { *; }
-keepclassmembers class com.maslarski.sudoku.** { *** Companion; }
-keepclasseswithmembers class com.maslarski.sudoku.** { kotlinx.serialization.KSerializer serializer(...); }

# Firestore document mapping
-keepclassmembers class com.maslarski.sudoku.data.remote.** { *; }
