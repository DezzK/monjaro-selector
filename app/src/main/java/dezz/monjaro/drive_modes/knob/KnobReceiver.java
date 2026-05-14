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

package dezz.monjaro.drive_modes.knob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import dezz.monjaro.drive_modes.service.DriveModeOverlayService;
import dezz.monjaro.drive_modes.util.Logs;

/**
 * Receives intents from MConfig+ and forwards a switch command to
 * {@link DriveModeOverlayService}. Multiplier = number of steps
 * (1, 2 or 3) depending on which exact intent arrived.
 *
 * Safety: on Android 14+ we verify the sender via {@link #getSentFromPackage()}
 * — only packages in the whitelist are accepted (MConfig+, MConfig, our own).
 * On API < 34 the sender package is unknown, so the check is skipped.
 */
public class KnobReceiver extends BroadcastReceiver {

    public static final String EXTRA_DIRECTION = "dezz.monjaro.drive_modes.extra.DIRECTION";
    public static final String EXTRA_STEPS = "dezz.monjaro.drive_modes.extra.STEPS";

    public static final String DIRECTION_PREV = "PREV";
    public static final String DIRECTION_NEXT = "NEXT";

    private static final Set<String> TRUSTED_SENDERS = new HashSet<>(Arrays.asList(
            "plus.monjaro",        // MConfig+
            "ru.monjaro.mconfig",  // original MConfig
            "dezz.monjaro.drive_modes"  // ourselves (for tests)
    ));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (!isSenderTrusted()) return;

        String action = intent.getAction();
        if (KnobIntents.ACTION_SHOW.equals(action)) {
            Logs.d("Knob intent: " + action + " (show overlay)");
            Intent svc = new Intent(context, DriveModeOverlayService.class);
            svc.setAction(DriveModeOverlayService.ACTION_SHOW_PREVIEW);
            startServiceSafe(context, svc);
            return;
        }

        String direction;
        int steps;
        switch (action) {
            case KnobIntents.ACTION_PREV_1: direction = DIRECTION_PREV; steps = 1; break;
            case KnobIntents.ACTION_PREV_2: direction = DIRECTION_PREV; steps = 2; break;
            case KnobIntents.ACTION_PREV_3: direction = DIRECTION_PREV; steps = 3; break;
            case KnobIntents.ACTION_NEXT_1: direction = DIRECTION_NEXT; steps = 1; break;
            case KnobIntents.ACTION_NEXT_2: direction = DIRECTION_NEXT; steps = 2; break;
            case KnobIntents.ACTION_NEXT_3: direction = DIRECTION_NEXT; steps = 3; break;
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
     * Sender check. On API 34+ we use {@link #getSentFromPackage()};
     * older Android versions do not expose such an API, so we skip the check.
     */
    private boolean isSenderTrusted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        String sender = getSentFromPackage();
        if (sender == null) {
            return true;
        }
        if (TRUSTED_SENDERS.contains(sender)) return true;
        Logs.w("Knob intent from an untrusted package: " + sender + " — ignored");
        return false;
    }
}
