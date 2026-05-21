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

package dezz.monjaro.drive_modes.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dezz.monjaro.drive_modes.car.DriveModeCatalog;
import dezz.monjaro.drive_modes.car.DriveModeDescriptor;
import dezz.monjaro.drive_modes.util.Logs;

public final class DriveModeSettings {

    private final SharedPreferences prefs;

    public DriveModeSettings(@NonNull Context context) {
        // Device-protected storage — accessible before user unlock. Important
        // because LOCKED_BOOT_COMPLETED is delivered before unlock and we need
        // to read settings (e.g. to decide whether to start the service).
        Context dps = context.getApplicationContext().createDeviceProtectedStorageContext();
        this.prefs = dps.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public boolean isDisableNativeUi() {
        return prefs.getBoolean(PreferenceKeys.KEY_DISABLE_NATIVE_UI, false);
    }

    public void setDisableNativeUi(boolean value) {
        prefs.edit().putBoolean(PreferenceKeys.KEY_DISABLE_NATIVE_UI, value).apply();
    }

    public int getAutoHidePreviewMs() {
        return prefs.getInt(PreferenceKeys.KEY_AUTO_HIDE_PREVIEW_MS,
                PreferenceKeys.DEFAULT_AUTO_HIDE_PREVIEW_MS);
    }

    public void setAutoHidePreviewMs(int ms) {
        prefs.edit().putInt(PreferenceKeys.KEY_AUTO_HIDE_PREVIEW_MS, clampAutoHide(ms)).apply();
    }

    public int getAutoHideSwitchMs() {
        return prefs.getInt(PreferenceKeys.KEY_AUTO_HIDE_SWITCH_MS,
                PreferenceKeys.DEFAULT_AUTO_HIDE_SWITCH_MS);
    }

    public void setAutoHideSwitchMs(int ms) {
        prefs.edit().putInt(PreferenceKeys.KEY_AUTO_HIDE_SWITCH_MS, clampAutoHide(ms)).apply();
    }

    private static int clampAutoHide(int ms) {
        return Math.max(PreferenceKeys.AUTO_HIDE_MIN_MS,
                Math.min(PreferenceKeys.AUTO_HIDE_MAX_MS, ms));
    }

    public boolean isDebugLogIntents() {
        return prefs.getBoolean(PreferenceKeys.KEY_DEBUG_LOG_INTENTS, false);
    }

    public void setDebugLogIntents(boolean value) {
        prefs.edit().putBoolean(PreferenceKeys.KEY_DEBUG_LOG_INTENTS, value).apply();
    }

    public boolean isCarouselMode() {
        return prefs.getBoolean(PreferenceKeys.KEY_CAROUSEL_MODE, false);
    }

    public void setCarouselMode(boolean value) {
        prefs.edit().putBoolean(PreferenceKeys.KEY_CAROUSEL_MODE, value).apply();
    }

    @NonNull
    public List<ModeOrderEntry> getOrder() {
        String json = prefs.getString(PreferenceKeys.KEY_MODE_ORDER, null);
        if (json == null) return defaultOrder();
        try {
            JSONArray arr = new JSONArray(json);
            List<ModeOrderEntry> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new ModeOrderEntry(obj.getInt("code"), obj.optBoolean("enabled", true)));
            }
            return result;
        } catch (JSONException e) {
            Logs.w("Failed to parse mode_order: " + e.getMessage());
            return defaultOrder();
        }
    }

    public void saveOrder(@NonNull List<ModeOrderEntry> order) {
        JSONArray arr = new JSONArray();
        for (ModeOrderEntry e : order) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("code", e.code);
                obj.put("enabled", e.enabled);
                arr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(PreferenceKeys.KEY_MODE_ORDER, arr.toString()).apply();
    }

    public void resetOrder() {
        prefs.edit().remove(PreferenceKeys.KEY_MODE_ORDER).apply();
    }

    public void setEnabled(int code, boolean enabled) {
        List<ModeOrderEntry> order = getOrder();
        for (ModeOrderEntry e : order) {
            if (e.code == code) {
                e.enabled = enabled;
                saveOrder(order);
                return;
            }
        }
        order.add(new ModeOrderEntry(code, enabled));
        saveOrder(order);
    }

    /**
     * Merges the saved order with the full catalog of modes.
     *
     * Returns ALL modes from the catalog (24 of them) so that the user can
     * enable any of them — including modes the car does not show in stock UI
     * but which can actually be activated (e.g. SAND on Monjaro).
     *
     * Order: saved entries first (preserving their enabled flag), then
     * catalog entries that were missing from the saved order.
     *
     * Default enabled for newly added entries: true if {@code supported}
     * contains the code, false otherwise.
     */
    @NonNull
    public List<ModeOrderEntry> mergeWithSupported(@NonNull int[] supported) {
        Set<Integer> supportedSet = new LinkedHashSet<>();
        for (int s : supported) supportedSet.add(s);

        Set<Integer> catalogSet = new LinkedHashSet<>();
        for (DriveModeDescriptor d : DriveModeCatalog.all()) catalogSet.add(d.code);

        List<ModeOrderEntry> existing = getOrder();
        List<ModeOrderEntry> merged = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();

        for (Iterator<ModeOrderEntry> it = existing.iterator(); it.hasNext(); ) {
            ModeOrderEntry e = it.next();
            if (catalogSet.contains(e.code) && !seen.contains(e.code)) {
                merged.add(e);
                seen.add(e.code);
            }
        }
        for (DriveModeDescriptor d : DriveModeCatalog.all()) {
            if (!seen.contains(d.code)) {
                merged.add(new ModeOrderEntry(d.code, supportedSet.contains(d.code)));
                seen.add(d.code);
            }
        }

        if (!merged.equals(existing)) {
            saveOrder(merged);
        }
        return merged;
    }

    /**
     * Result of the auto-probe: force enabled = (code is in {@code probed}),
     * disabled = everything else from the catalog. Inside each section the
     * relative order from the previous save is preserved so the user's
     * preferred ordering of currently-active modes survives a re-probe.
     */
    @NonNull
    public List<ModeOrderEntry> applyProbedSupported(@NonNull int[] probed) {
        java.util.Set<Integer> probedSet = new LinkedHashSet<>();
        for (int p : probed) probedSet.add(p);

        java.util.Set<Integer> catalogSet = new LinkedHashSet<>();
        for (DriveModeDescriptor d : DriveModeCatalog.all()) catalogSet.add(d.code);

        List<ModeOrderEntry> existing = getOrder();
        List<ModeOrderEntry> enabled = new ArrayList<>();
        List<ModeOrderEntry> disabled = new ArrayList<>();
        java.util.Set<Integer> seen = new LinkedHashSet<>();

        // 1. Walk previously saved order: split into enabled (probed-supported)
        //    and disabled (the rest), respecting the saved relative ordering.
        for (ModeOrderEntry e : existing) {
            if (!catalogSet.contains(e.code) || seen.contains(e.code)) continue;
            seen.add(e.code);
            if (probedSet.contains(e.code)) {
                enabled.add(new ModeOrderEntry(e.code, true));
            } else {
                disabled.add(new ModeOrderEntry(e.code, false));
            }
        }
        // 2. Probed codes that weren't in the saved order yet — append at the
        //    end of enabled in the probe-input order.
        for (int code : probed) {
            if (!seen.contains(code) && catalogSet.contains(code)) {
                enabled.add(new ModeOrderEntry(code, true));
                seen.add(code);
            }
        }
        // 3. Anything else from the catalog goes to the disabled tail.
        for (DriveModeDescriptor d : DriveModeCatalog.all()) {
            if (!seen.contains(d.code)) {
                disabled.add(new ModeOrderEntry(d.code, false));
                seen.add(d.code);
            }
        }

        List<ModeOrderEntry> result = new ArrayList<>(enabled);
        result.addAll(disabled);
        saveOrder(result);
        return result;
    }

    /**
     * Default at first run (before the supported list arrives from the SDK).
     * Nothing is enabled by default — that is filled in by {@link #mergeWithSupported}.
     */
    @NonNull
    private List<ModeOrderEntry> defaultOrder() {
        List<ModeOrderEntry> list = new ArrayList<>();
        for (DriveModeDescriptor d : DriveModeCatalog.all()) {
            list.add(new ModeOrderEntry(d.code, false));
        }
        return list;
    }
}
