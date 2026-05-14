package ru.monjaro.selector.knob;

/**
 * Intent actions для приёма событий поворота физического селектора drive mode.
 * MConfig+ настраивается на отправку этих intent'ов при срабатывании knob.
 *
 * Привязка для MConfig+:
 *   knobLeftClick1Cfg  → "Отправить намерение" → action: KNOB_LEFT_1   package: ru.monjaro.selector
 *   knobRightClick1Cfg → "Отправить намерение" → action: KNOB_RIGHT_1  package: ru.monjaro.selector
 * (опционально для серий 2/3 кликов — отдельные действия в наших настройках).
 */
public final class KnobIntents {

    public static final String ACTION_KNOB_LEFT_1 =
            "ru.monjaro.selector.action.KNOB_LEFT_1";
    public static final String ACTION_KNOB_LEFT_2 =
            "ru.monjaro.selector.action.KNOB_LEFT_2";
    public static final String ACTION_KNOB_LEFT_3 =
            "ru.monjaro.selector.action.KNOB_LEFT_3";

    public static final String ACTION_KNOB_RIGHT_1 =
            "ru.monjaro.selector.action.KNOB_RIGHT_1";
    public static final String ACTION_KNOB_RIGHT_2 =
            "ru.monjaro.selector.action.KNOB_RIGHT_2";
    public static final String ACTION_KNOB_RIGHT_3 =
            "ru.monjaro.selector.action.KNOB_RIGHT_3";

    /**
     * Просто показать оверлей с текущим режимом. Без смены режима.
     * Используется для сценария «кнопка на руле → показать селектор для тапа».
     */
    public static final String ACTION_SHOW_OVERLAY =
            "ru.monjaro.selector.action.SHOW_OVERLAY";

    private KnobIntents() {}
}
