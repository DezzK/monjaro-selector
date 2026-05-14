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

package dezz.monjaro.drive_modes.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import dezz.monjaro.drive_modes.service.DriveModeOverlayService;

/**
 * Receiver that autostarts the overlay service after boot.
 * Handles BOOT_COMPLETED and QUICKBOOT_POWERON (Samsung-style early boot).
 *
 * Not registered for LOCKED_BOOT_COMPLETED because the Service we launch is
 * not directBootAware and the ECarX SDK is typically only reachable after
 * user unlock.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "MonjaroDriveModes.BootReceiver";

    private static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !ACTION_QUICKBOOT_POWERON.equals(action)) {
            return;
        }
        Log.d(TAG, "Device boot completed (" + action + "), checking autostart");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "No SYSTEM_ALERT_WINDOW permission yet — not starting the service");
            return;
        }

        if (DriveModeOverlayService.isRunning()) {
            Log.d(TAG, "Service is already running — not starting it again");
            return;
        }

        Log.i(TAG, "Auto-starting drive mode overlay service");
        Intent serviceIntent = new Intent(context, DriveModeOverlayService.class);
        try {
            context.startForegroundService(serviceIntent);
        } catch (Throwable t) {
            Log.w(TAG, "startForegroundService failed: " + t.getMessage());
        }
    }
}
