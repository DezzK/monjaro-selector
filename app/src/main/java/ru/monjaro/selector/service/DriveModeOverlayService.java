package ru.monjaro.selector.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.view.ContextThemeWrapper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.monjaro.selector.MonjaroSelectorApp;
import ru.monjaro.selector.R;
import ru.monjaro.selector.car.DriveModeChangeOrigin;
import ru.monjaro.selector.car.DriveModeRepository;
import ru.monjaro.selector.car.RotationDirection;
import ru.monjaro.selector.knob.KnobReceiver;
import ru.monjaro.selector.settings.DriveModeSettings;
import ru.monjaro.selector.settings.ModeOrderEntry;
import ru.monjaro.selector.settings.PreferenceKeys;
import ru.monjaro.selector.ui.overlay.OverlayController;
import ru.monjaro.selector.util.Logs;

public class DriveModeOverlayService extends Service
        implements DriveModeRepository.Listener,
                   DriveModeRepository.SupportedModesListener,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_KNOB_STEP =
            "ru.monjaro.selector.service.action.KNOB_STEP";
    public static final String ACTION_SHOW_PREVIEW =
            "ru.monjaro.selector.service.action.SHOW_PREVIEW";

    private DriveModeRepository repository;
    private DriveModeSettings settings;
    private OverlayController overlay;
    private int lastNightModeMask = Configuration.UI_MODE_NIGHT_UNDEFINED;

    /**
     * Кешированные данные на UI-потоке. Перевычисляются при изменении
     * {@code supportedModes} (через {@link DriveModeRepository.SupportedModesListener})
     * или при изменении SharedPreferences (через
     * {@link SharedPreferences.OnSharedPreferenceChangeListener}).
     */
    private List<Integer> enabledOrderCache = Collections.emptyList();
    private Set<Integer> enabledCodesCache = Collections.emptySet();

    @Override
    public void onCreate() {
        super.onCreate();
        Logs.d("DriveModeOverlayService onCreate");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.build(this),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.build(this));
        }

        settings = MonjaroSelectorApp.get(this).getSettings();
        repository = DriveModeRepository.get();
        repository.init(this);
        repository.addListener(this);
        repository.addSupportedModesListener(this);

        overlay = createOverlayForCurrentTheme();
        lastNightModeMask = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        recomputeEnabledCache(repository.getSupportedModes());

        settings.getPrefs().registerOnSharedPreferenceChangeListener(this);
    }

    private OverlayController createOverlayForCurrentTheme() {
        ContextThemeWrapper themed = new ContextThemeWrapper(
                this, R.style.Theme_MonjaroSelector);
        OverlayController c = new OverlayController(themed);
        c.setAutoHideMs(settings.getAutoHideMs());
        c.setOnModeTapListener(this::handleModeTap);
        return c;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int newNight = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (newNight != lastNightModeMask) {
            Logs.d("UI mode changed (night=" + newNight + ") — пересоздаём оверлей");
            lastNightModeMask = newNight;
            if (overlay != null) overlay.dispose();
            overlay = createOverlayForCurrentTheme();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_KNOB_STEP:
                    String dir = intent.getStringExtra(KnobReceiver.EXTRA_DIRECTION);
                    int steps = intent.getIntExtra(KnobReceiver.EXTRA_STEPS, 1);
                    handleKnobStep(dir, steps);
                    break;
                case ACTION_SHOW_PREVIEW:
                    handleShowPreview();
                    break;
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Logs.d("DriveModeOverlayService onDestroy");
        if (settings != null) {
            settings.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        }
        if (repository != null) {
            repository.removeListener(this);
            repository.removeSupportedModesListener(this);
        }
        if (overlay != null) {
            overlay.dispose();
        }
        super.onDestroy();
    }

    @MainThread
    @Override
    public void onModeChanged(int previousValue, int newValue, @NonNull DriveModeChangeOrigin origin) {
        if (origin == DriveModeChangeOrigin.PROGRAMMATIC) {
            return;
        }

        // Режим менять программно НЕ имеем права — единственный легитимный
        // триггер для смены это knob или тап по pill. Тут мы только
        // показываем оверлей, если кто-то снаружи (голос, MConfig) сменил
        // mode на один из enabled. Если режим выключен в наших настройках —
        // молча игнорируем, чтобы не дёргать машину при глушении зажигания,
        // запуске приложения и т.п. фоновых событиях ECU.
        List<Integer> enabledOrder = enabledOrderCache;
        if (enabledOrder.isEmpty()) return;
        if (!enabledCodesCache.contains(newValue)) return;
        overlay.show(enabledOrder, newValue);
    }

    @MainThread
    @Override
    public void onSupportedModesChanged(@NonNull int[] supportedModes) {
        Logs.d("Supported modes updated, size=" + supportedModes.length);
        recomputeEnabledCache(supportedModes);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (PreferenceKeys.KEY_OVERLAY_AUTO_HIDE_MS.equals(key) && overlay != null) {
            overlay.setAutoHideMs(settings.getAutoHideMs());
        }
        if (PreferenceKeys.KEY_MODE_ORDER.equals(key) || key == null) {
            recomputeEnabledCache(repository.getSupportedModes());
        }
    }

    /**
     * Перестраивает кеш enabledOrder/enabledCodes из текущих настроек и
     * предоставленного списка supported. Также persistит merged-список,
     * если supported изменился относительно сохранённого порядка.
     */
    @MainThread
    private void recomputeEnabledCache(@NonNull int[] supportedModes) {
        List<ModeOrderEntry> order = settings.mergeWithSupported(supportedModes);
        List<Integer> newOrder = new ArrayList<>();
        Set<Integer> newCodes = new HashSet<>();
        for (ModeOrderEntry e : order) {
            if (e.enabled) {
                newOrder.add(e.code);
                newCodes.add(e.code);
            }
        }
        enabledOrderCache = Collections.unmodifiableList(newOrder);
        enabledCodesCache = Collections.unmodifiableSet(newCodes);
    }

    /**
     * Обработка команды knob-шага от {@link KnobReceiver}.
     *
     * Для серий 2/3 кликов: фактически режим устанавливается на финальный
     * сразу (без задержек), а оверлей анимированно проходит через все
     * промежуточные положения.
     */
    private void handleKnobStep(String direction, int steps) {
        Logs.d("Knob step: direction=" + direction + ", steps=" + steps);
        if (direction == null || steps < 1) return;
        List<Integer> enabledOrder = enabledOrderCache;
        if (enabledOrder.size() < 2) {
            Logs.d("Knob step: не из чего выбирать (enabled<2)");
            if (!enabledOrder.isEmpty()) {
                overlay.show(enabledOrder, repository.getLastKnownMode());
            }
            return;
        }
        repository.readCurrentModeAsync(actual -> {
            RotationDirection dir = KnobReceiver.DIRECTION_LEFT.equals(direction)
                    ? RotationDirection.BACKWARD
                    : RotationDirection.FORWARD;

            List<Integer> intermediateCodes = enumerateSteps(actual, dir, steps, enabledOrder);
            if (intermediateCodes.isEmpty()) {
                overlay.show(enabledOrder, actual);
                return;
            }
            int finalMode = intermediateCodes.get(intermediateCodes.size() - 1);
            if (finalMode != actual) {
                repository.setMode(finalMode, DriveModeChangeOrigin.PROGRAMMATIC);
            }
            if (intermediateCodes.size() == 1) {
                overlay.show(enabledOrder, finalMode);
            } else {
                overlay.animateStepsTo(enabledOrder, actual, intermediateCodes);
            }
        });
    }

    private void handleShowPreview() {
        List<Integer> enabledOrder = enabledOrderCache;
        if (enabledOrder.isEmpty()) {
            Logs.d("Preview: нет включённых режимов");
            return;
        }
        repository.readCurrentModeAsync(actual -> {
            int show = enabledOrder.contains(actual) ? actual : enabledOrder.get(0);
            overlay.show(enabledOrder, show);
        });
    }

    /** Обработка тапа по pill оверлея — переключиться на этот режим. */
    @MainThread
    private void handleModeTap(int modeCode) {
        Logs.d("Pill tap: " + modeCode);
        if (!enabledCodesCache.contains(modeCode)) return;
        repository.setMode(modeCode, DriveModeChangeOrigin.PROGRAMMATIC);
        overlay.show(enabledOrderCache, modeCode);
    }

    /**
     * Возвращает список промежуточных кодов (включая финальный) для серии
     * из N шагов по {@code enabledOrder} (по кругу).
     */
    static List<Integer> enumerateSteps(int start,
                                        RotationDirection direction,
                                        int steps,
                                        List<Integer> enabledOrder) {
        List<Integer> result = new ArrayList<>();
        int n = enabledOrder.size();
        if (n == 0) return result;
        int idx = enabledOrder.indexOf(start);
        int sign = direction == RotationDirection.FORWARD ? 1 : -1;
        if (idx < 0) {
            idx = direction == RotationDirection.FORWARD ? -1 : n;
        }
        for (int k = 1; k <= steps; k++) {
            int j = ((idx + sign * k) % n + n) % n;
            result.add(enabledOrder.get(j));
        }
        return result;
    }
}
