# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep all classes that extend Service
-keep public class * extends android.app.Service

# Keep all BroadcastReceivers
-keep public class * extends android.content.BroadcastReceiver

# Keep all NotificationListenerService
-keep public class * extends android.service.notification.NotificationListenerService

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep security-related classes
-keep class androidx.security.crypto.** { *; }

# Obfuscate class and method names (security)
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep module interface for dynamic loading
-keep interface com.aman.agent.modules.base.BaseModule
-keep class * implements com.aman.agent.modules.base.BaseModule

# Anti-tampering: Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serialization classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Aggressive obfuscation
-overloadaggressively
-repackageclasses ''
-allowaccessmodification

# Remove unused code
-dontwarn **
-ignorewarnings
