package ru.monjaro.selector.knob;

/**
 * Intent actions used to receive mode switch events from MConfig+.
 *
 * Binding in MConfig+:
 *   knobLeftClick1Cfg  -> "Send intent" -> action: PREV_1   package: ru.monjaro.selector
 *   knobRightClick1Cfg -> "Send intent" -> action: NEXT_1   package: ru.monjaro.selector
 * (separate actions for 2/3-click series are optional).
 */
public final class KnobIntents {

    public static final String ACTION_PREV_1 =
            "ru.monjaro.selector.action.PREV_1";
    public static final String ACTION_PREV_2 =
            "ru.monjaro.selector.action.PREV_2";
    public static final String ACTION_PREV_3 =
            "ru.monjaro.selector.action.PREV_3";

    public static final String ACTION_NEXT_1 =
            "ru.monjaro.selector.action.NEXT_1";
    public static final String ACTION_NEXT_2 =
            "ru.monjaro.selector.action.NEXT_2";
    public static final String ACTION_NEXT_3 =
            "ru.monjaro.selector.action.NEXT_3";

    /**
     * Just show the overlay with the current mode. Does not change the mode.
     * Used for the "steering wheel button -> show selector to tap" scenario.
     */
    public static final String ACTION_SHOW_OVERLAY =
            "ru.monjaro.selector.action.SHOW_OVERLAY";

    private KnobIntents() {}
}
