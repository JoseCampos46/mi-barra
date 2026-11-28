# Vosk y JNA usan código nativo: no se deben renombrar
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Versión 46: ChpInicio abre las pantallas de ChpBar por su nombre: no se deben renombrar
-keepnames class co.jose.mibarra.** extends android.app.Activity
-keepnames class co.jose.mibarra.** extends android.app.Service
