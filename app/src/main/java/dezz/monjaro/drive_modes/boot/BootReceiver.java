package dezz.monjaro.drive_modes.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import dezz.monjaro.drive_modes.service.DriveModeOverlayService;
import dezz.monjaro.drive_modes.util.Logs;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Logs.d("Boot intent: " + action);
        try {
            Intent svc = new Intent(context, DriveModeOverlayService.class);
            ContextCompat.startForegroundService(context, svc);
        } catch (Throwable t) {
            Logs.w("BootReceiver startService failed: " + t.getMessage());
        }
    }
}
