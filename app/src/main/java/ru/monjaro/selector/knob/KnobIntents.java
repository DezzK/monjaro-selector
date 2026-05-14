package ru.monjaro.selector.knob;

/**
 * Intent actions для приёма событий переключения режима от MConfig+.
 *
 * Привязка для MConfig+:
 *   knobLeftClick1Cfg  → «Отправить намерение» → action: PREV_1   package: ru.monjaro.selector
 *   knobRightClick1Cfg → «Отправить намерение» → action: NEXT_1   package: ru.monjaro.selector
 * (опционально для серий 2/3 кликов — отдельные actions).
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
     * Просто показать оверлей с текущим режимом. Без смены режима.
     * Используется для сценария «кнопка на руле → показать селектор для тапа».
     */
    public static final String ACTION_SHOW_OVERLAY =
            "ru.monjaro.selector.action.SHOW_OVERLAY";

    private KnobIntents() {}
}
