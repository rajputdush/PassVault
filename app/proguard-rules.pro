# Argon2Kt calls into native code through JNI, so its classes must keep their names.
-keep class com.lambdapioneer.argon2kt.** { *; }

# Keep line numbers in crash reports from release builds, hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
