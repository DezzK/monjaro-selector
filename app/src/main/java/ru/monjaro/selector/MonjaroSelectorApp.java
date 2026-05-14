package ru.monjaro.selector;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import ru.monjaro.selector.car.DriveModeRepository;
import ru.monjaro.selector.service.DriveModeOverlayService;
import ru.monjaro.selector.settings.DriveModeSettings;
import ru.monjaro.selector.util.Logs;

public class MonjaroSelectorApp extends Application {

    private DriveModeSettings settings;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new DriveModeSettings(this);
        DriveModeRepository.get().init(this);
        // The service is started either from BootReceiver (on boot) or from
        // MainActivity.onResume (when the user grants overlay permission).
        // We do not start it here to avoid racing with BootReceiver and to
        // avoid attempting a start without overlay permission.
    }

    public DriveModeSettings getSettings() {
        return settings;
    }

    public static MonjaroSelectorApp get(android.content.Context ctx) {
        return (MonjaroSelectorApp) ctx.getApplicationContext();
    }

    public void startServiceIfPermitted() {
        boolean canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        if (!canOverlay) {
            Logs.w("No SYSTEM_ALERT_WINDOW permission — not starting the service");
            return;
        }
        Intent intent = new Intent(this, DriveModeOverlayService.class);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (Throwable t) {
            Logs.w("startForegroundService failed: " + t.getMessage());
        }
    }
}
