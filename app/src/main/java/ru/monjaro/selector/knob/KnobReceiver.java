package ru.monjaro.selector.knob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ru.monjaro.selector.service.DriveModeOverlayService;
import ru.monjaro.selector.util.Logs;

/**
 * Принимает intent'ы от MConfig+ и передаёт команду переключения в
 * {@link DriveModeOverlayService}. Multiplier = количество шагов
 * (1, 2 или 3) согласно тому какой именно интент пришёл.
 *
 * Safety: на Android 14+ проверяем отправителя через {@link #getSentFromPackage()}
 * — пропускаем только из whitelist (MConfig+, MConfig, наш собственный пакет).
 * На API < 34 такой проверки нет — sender package неизвестен.
 */
public class KnobReceiver extends BroadcastReceiver {

    public static final String EXTRA_DIRECTION = "ru.monjaro.selector.extra.DIRECTION";
    public static final String EXTRA_STEPS = "ru.monjaro.selector.extra.STEPS";

    public static final String DIRECTION_LEFT = "LEFT";
    public static final String DIRECTION_RIGHT = "RIGHT";

    private static final Set<String> TRUSTED_SENDERS = new HashSet<>(Arrays.asList(
            "plus.monjaro",        // MConfig+
            "ru.monjaro.mconfig",  // оригинальный MConfig
            "ru.monjaro.selector"  // мы сами (для тестов)
    ));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (!isSenderTrusted()) return;

        String action = intent.getAction();
        if (KnobIntents.ACTION_SHOW_OVERLAY.equals(action)) {
            Logs.d("Knob intent: " + action + " (show overlay)");
            Intent svc = new Intent(context, DriveModeOverlayService.class);
            svc.setAction(DriveModeOverlayService.ACTION_SHOW_PREVIEW);
            startServiceSafe(context, svc);
            return;
        }

        String direction;
        int steps;
        switch (action) {
            case KnobIntents.ACTION_KNOB_LEFT_1:  direction = DIRECTION_LEFT;  steps = 1; break;
            case KnobIntents.ACTION_KNOB_LEFT_2:  direction = DIRECTION_LEFT;  steps = 2; break;
            case KnobIntents.ACTION_KNOB_LEFT_3:  direction = DIRECTION_LEFT;  steps = 3; break;
            case KnobIntents.ACTION_KNOB_RIGHT_1: direction = DIRECTION_RIGHT; steps = 1; break;
            case KnobIntents.ACTION_KNOB_RIGHT_2: direction = DIRECTION_RIGHT; steps = 2; break;
            case KnobIntents.ACTION_KNOB_RIGHT_3: direction = DIRECTION_RIGHT; steps = 3; break;
            default: return;
        }
        Logs.d("Knob intent: " + action + " (direction=" + direction + ", steps=" + steps + ")");

        Intent svc = new Intent(context, DriveModeOverlayService.class);
        svc.setAction(DriveModeOverlayService.ACTION_KNOB_STEP);
        svc.putExtra(EXTRA_DIRECTION, direction);
        svc.putExtra(EXTRA_STEPS, steps);
        startServiceSafe(context, svc);
    }

    private static void startServiceSafe(Context context, Intent svc) {
        try {
            ContextCompat.startForegroundService(context, svc);
        } catch (Throwable t) {
            Logs.w("KnobReceiver startService failed: " + t.getMessage());
        }
    }

    /**
     * Проверка отправителя. На API 34+ используем {@link #getSentFromPackage()};
     * на старых версиях Android API такой проверки не существует — пропускаем.
     */
    private boolean isSenderTrusted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        String sender = getSentFromPackage();
        if (sender == null) {
            // System broadcasts либо отправитель неизвестен — пропускаем.
            return true;
        }
        if (TRUSTED_SENDERS.contains(sender)) return true;
        Logs.w("Knob intent от недоверенного пакета: " + sender + " — игнорируем");
        return false;
    }
}
