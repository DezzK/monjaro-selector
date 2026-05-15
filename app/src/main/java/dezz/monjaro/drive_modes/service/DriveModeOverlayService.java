/*
 * Copyright © 2026 Dezz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dezz.monjaro.drive_modes.service;

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

import dezz.monjaro.drive_modes.MonjaroSelectorApp;
import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.car.DriveModeChangeOrigin;
import dezz.monjaro.drive_modes.car.DriveModeRepository;
import dezz.monjaro.drive_modes.car.RotationDirection;
import dezz.monjaro.drive_modes.knob.KnobReceiver;
import dezz.monjaro.drive_modes.settings.DriveModeSettings;
import dezz.monjaro.drive_modes.settings.ModeOrderEntry;
import dezz.monjaro.drive_modes.settings.PreferenceKeys;
import dezz.monjaro.drive_modes.ui.overlay.OverlayController;
import dezz.monjaro.drive_modes.util.Logs;

public class DriveModeOverlayService extends Service
        implements DriveModeRepository.Listener,
                   DriveModeRepository.SupportedModesListener,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_KNOB_STEP =
            "dezz.monjaro.drive_modes.service.KNOB_STEP";
    public static final String ACTION_SHOW_PREVIEW =
            "dezz.monjaro.drive_modes.service.SHOW_PREVIEW";

    /**
     * Auto-hide after a pill tap. The user made an explicit choice — there is
     * no point keeping the overlay around, a short visual confirmation flash
     * is enough.
     */
    private static final int AUTO_HIDE_AFTER_TAP_MS = 500;

    private static DriveModeOverlayService instance;

    /** Returns true while this service is alive (between onCreate and onDestroy). */
    public static boolean isRunning() {
        return instance != null;
    }

    private DriveModeRepository repository;
    private DriveModeSettings settings;
    private OverlayController overlay;
    private int lastNightModeMask = Configuration.UI_MODE_NIGHT_UNDEFINED;

    /**
     * Cached data on the UI thread. Recomputed when {@code supportedModes}
     * changes (via {@link DriveModeRepository.SupportedModesListener}) or
     * when SharedPreferences change (via
     * {@link SharedPreferences.OnSharedPreferenceChangeListener}).
     */
    private List<Integer> enabledOrderCache = Collections.emptyList();
    private Set<Integer> enabledCodesCache = Collections.emptySet();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
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
                this, R.style.Theme_MonjaroDriveModes);
        OverlayController c = new OverlayController(themed);
        c.setOnModeTapListener(this::handleModeTap);
        c.setCarouselMode(settings.isCarouselMode());
        return c;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int newNight = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (newNight != lastNightModeMask) {
            Logs.d("UI mode changed (night=" + newNight + ") — recreating overlay");
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
        instance = null;
        if (settings != null) {
            settings.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        }
        if (repository != null) {
            repository.removeListener(this);
            repository.removeSupportedModesListener(this);
            // The SDK binding lives in a singleton: clean up watcher + Car
            // references so a future service restart starts from a clean slate.
            repository.shutdown();
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

        // We have no right to change the mode programmatically here — the only
        // legitimate triggers for a switch are knob and pill tap. Here we just
        // show the overlay if someone external (voice, MConfig) switched to a
        // mode that is in the enabled list. If the mode is disabled in our
        // settings we silently ignore the event — we don't want to mess with
        // the car on ignition off, app start and other background ECU events.
        List<Integer> enabledOrder = enabledOrderCache;
        if (enabledOrder.isEmpty()) return;
        if (!enabledCodesCache.contains(newValue)) return;
        overlay.show(enabledOrder, newValue, settings.getAutoHideSwitchMs());
    }

    @MainThread
    @Override
    public void onSupportedModesChanged(@NonNull int[] supportedModes) {
        Logs.d("Supported modes updated, size=" + supportedModes.length);
        recomputeEnabledCache(supportedModes);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (PreferenceKeys.KEY_MODE_ORDER.equals(key) || key == null) {
            recomputeEnabledCache(repository.getSupportedModes());
        }
        if (PreferenceKeys.KEY_CAROUSEL_MODE.equals(key) || key == null) {
            if (overlay != null) overlay.setCarouselMode(settings.isCarouselMode());
        }
        // KEY_AUTO_HIDE_* are read directly at show time — no extra handling needed.
    }

    /**
     * Rebuilds enabledOrder/enabledCodes cache from current settings and the
     * provided supported list. Also persists the merged list if supported has
     * changed relative to the saved order.
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
     * Handles a knob-step command from {@link KnobReceiver}.
     *
     * For 2/3-click series: the actual mode is set to the final value
     * immediately (no delays), while the overlay animates through all
     * intermediate positions.
     */
    private void handleKnobStep(String direction, int steps) {
        Logs.d("Knob step: direction=" + direction + ", steps=" + steps);
        if (direction == null || steps < 1) return;
        List<Integer> enabledOrder = enabledOrderCache;
        int switchMs = settings.getAutoHideSwitchMs();
        if (enabledOrder.size() < 2) {
            Logs.d("Knob step: nothing to switch between (enabled<2)");
            if (!enabledOrder.isEmpty()) {
                overlay.show(enabledOrder, repository.getLastKnownMode(), switchMs);
            }
            return;
        }
        repository.readCurrentModeAsync(actual -> {
            if (actual < 0) {
                // Repository hasn't read a real value from the SDK yet (e.g.
                // very first knob step right after boot, while initOnIo is
                // still racing). Skipping is much better than picking a
                // random fallback and yanking ECU to the first enabled mode.
                Logs.w("Knob step ignored — current mode not yet known");
                return;
            }
            RotationDirection dir = KnobReceiver.DIRECTION_PREV.equals(direction)
                    ? RotationDirection.BACKWARD
                    : RotationDirection.FORWARD;

            List<Integer> intermediateCodes = enumerateSteps(actual, dir, steps, enabledOrder);
            if (intermediateCodes.isEmpty()) {
                overlay.show(enabledOrder, actual, switchMs);
                return;
            }
            int finalMode = intermediateCodes.get(intermediateCodes.size() - 1);
            if (finalMode != actual) {
                repository.setMode(finalMode, DriveModeChangeOrigin.PROGRAMMATIC);
            }
            if (intermediateCodes.size() == 1) {
                overlay.show(enabledOrder, finalMode, switchMs);
            } else {
                overlay.animateStepsTo(enabledOrder, actual, intermediateCodes, switchMs);
            }
        });
    }

    private void handleShowPreview() {
        List<Integer> enabledOrder = enabledOrderCache;
        if (enabledOrder.isEmpty()) {
            Logs.d("Preview: no enabled modes");
            return;
        }
        int previewMs = settings.getAutoHidePreviewMs();
        repository.readCurrentModeAsync(actual -> {
            int show = enabledOrder.contains(actual) ? actual : enabledOrder.get(0);
            overlay.show(enabledOrder, show, previewMs);
        });
    }

    /** Handles a tap on an overlay pill — switch to that mode. */
    @MainThread
    private void handleModeTap(int modeCode) {
        Logs.d("Pill tap: " + modeCode);
        if (!enabledCodesCache.contains(modeCode)) return;
        repository.setMode(modeCode, DriveModeChangeOrigin.PROGRAMMATIC);
        overlay.show(enabledOrderCache, modeCode, AUTO_HIDE_AFTER_TAP_MS);
    }

    /**
     * Returns the list of intermediate codes (including the final one) for a
     * series of N steps over {@code enabledOrder} (wrapping around).
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
