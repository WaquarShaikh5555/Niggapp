package app.undo.engine

/** Human wording for the system settings UNDO watches. Values are stored as strings. */
object SettingWords {
    const val ROTATION = "rotation"
    const val BRIGHTNESS_MODE = "brightness_mode"
    const val TIMEOUT = "timeout"
    const val FONT_SCALE = "font_scale"
    const val AIRPLANE = "airplane"
    const val RINGER = "ringer"
    const val DND = "dnd"

    fun label(key: String): String = when (key) {
        ROTATION -> "Auto-rotate"
        BRIGHTNESS_MODE -> "Adaptive brightness"
        TIMEOUT -> "Screen timeout"
        FONT_SCALE -> "Font size"
        AIRPLANE -> "Airplane mode"
        RINGER -> "Sound mode"
        DND -> "Do Not Disturb"
        else -> key
    }

    fun value(key: String, v: String?): String {
        if (v == null) return "unknown"
        return when (key) {
            ROTATION, BRIGHTNESS_MODE, AIRPLANE -> if (v == "1") "On" else "Off"
            TIMEOUT -> v.toLongOrNull()?.let { duration(it) } ?: v
            FONT_SCALE -> v.toFloatOrNull()?.let { "${Math.round(it * 100)}%" } ?: v
            RINGER -> when (v) { "0" -> "Silent"; "1" -> "Vibrate"; "2" -> "Sound on"; else -> v }
            DND -> when (v) { "1" -> "Off"; "2" -> "Priority only"; "3" -> "Total silence"; "4" -> "Alarms only"; else -> "Unknown" }
            else -> v
        }
    }

    fun title(key: String, old: String?, new: String?): String = when (key) {
        ROTATION -> if (new == "1") "Auto-rotate turned on" else "Auto-rotate turned off"
        BRIGHTNESS_MODE -> if (new == "1") "Adaptive brightness turned on" else "Adaptive brightness turned off"
        AIRPLANE -> if (new == "1") "Airplane mode turned on" else "Airplane mode turned off"
        DND -> if (new == "1") "Do Not Disturb turned off" else "Do Not Disturb turned on (${value(key, new)})"
        RINGER -> "Phone switched to ${value(key, new)}"
        else -> "${label(key)} changed to ${value(key, new)}"
    }

    private fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s >= 3600 * 24 -> "never"
            s >= 3600 -> "${s / 3600} hr"
            s >= 60 -> "${s / 60} min"
            else -> "$s sec"
        }
    }
}
