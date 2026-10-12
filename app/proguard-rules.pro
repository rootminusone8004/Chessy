# ProGuard configuration for Chessy
-keepattributes SourceFile,LineNumberTable

# Preserve model classes
-keep class com.mhrahmankwoshik.chessy.data.model.** { *; }

# Preserve custom views and view bindings
-keep class com.mhrahmankwoshik.chessy.ui.ChessBoardView { *; }
-keep class com.mhrahmankwoshik.chessy.databinding.** { *; }
