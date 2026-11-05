# Release build rules for Selawi Dash.

# The 3D engine (Filament and its glTF loader) is kept whole. Its native code calls back into its
# Java classes; the engine ships rules for that, and this keeps everything else of it as well.
-keep class com.google.android.filament.** { *; }
-keepclassmembers class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# Readable stack traces in the problem report.
-keepattributes SourceFile,LineNumberTable
