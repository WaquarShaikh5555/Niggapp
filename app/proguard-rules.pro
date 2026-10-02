# UNDO uses no reflection-based serialization. Keep the notification listener (bound by the system by name).
-keep class app.undo.capture.UndoNotificationListener { *; }
# Strip all android.util.Log calls from release builds (defence in depth: never log user content).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
