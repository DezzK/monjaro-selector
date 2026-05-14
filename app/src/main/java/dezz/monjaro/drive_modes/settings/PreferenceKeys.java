package dezz.monjaro.drive_modes.settings;

public final class PreferenceKeys {

    public static final String PREFS_NAME = "monjaro_drive_modes_prefs";

    public static final String KEY_MODE_ORDER = "mode_order";
    public static final String KEY_DISABLE_NATIVE_UI = "disable_native_ui";
    public static final String KEY_AUTO_HIDE_PREVIEW_MS = "auto_hide_preview_ms";
    public static final String KEY_AUTO_HIDE_SWITCH_MS = "auto_hide_switch_ms";
    public static final String KEY_DEBUG_LOG_INTENTS = "debug_log_intents";

    /** Longer than switch: user explicitly opened the overlay and needs time to look at it and tap. */
    public static final int DEFAULT_AUTO_HIDE_PREVIEW_MS = 5000;
    /** Shorter: after a switch we only need to visually confirm the new mode. */
    public static final int DEFAULT_AUTO_HIDE_SWITCH_MS = 3000;

    public static final int AUTO_HIDE_MIN_MS = 1500;
    public static final int AUTO_HIDE_MAX_MS = 10000;

    private PreferenceKeys() {}
}
