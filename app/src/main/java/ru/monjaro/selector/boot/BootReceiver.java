package ru.monjaro.selector.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import ru.monjaro.selector.service.DriveModeOverlayService;
import ru.monjaro.selector.util.Logs;

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
