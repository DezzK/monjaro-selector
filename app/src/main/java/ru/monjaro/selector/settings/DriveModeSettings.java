package ru.monjaro.selector.settings;

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

import ru.monjaro.selector.car.DriveModeCatalog;
import ru.monjaro.selector.car.DriveModeDescriptor;
import ru.monjaro.selector.util.Logs;

public final class DriveModeSettings {

    private final SharedPreferences prefs;

    public DriveModeSettings(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE);
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

    public int getAutoHideMs() {
        return prefs.getInt(PreferenceKeys.KEY_OVERLAY_AUTO_HIDE_MS, PreferenceKeys.DEFAULT_AUTO_HIDE_MS);
    }

    public boolean isDebugLogIntents() {
        return prefs.getBoolean(PreferenceKeys.KEY_DEBUG_LOG_INTENTS, false);
    }

    public void setDebugLogIntents(boolean value) {
        prefs.edit().putBoolean(PreferenceKeys.KEY_DEBUG_LOG_INTENTS, value).apply();
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
            Logs.w("Не удалось распарсить mode_order: " + e.getMessage());
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
     * Объединяет сохранённый порядок с каталогом всех режимов.
     *
     * Возвращает ВСЕ режимы из каталога (24 шт), чтобы пользователь мог
     * включить любой — в том числе те, что машина «штатно не показывает»,
     * но фактически их можно активировать (например SAND на Monjaro).
     *
     * Порядок: сначала режимы из saved order (с сохранением их enabled),
     * потом — режимы из каталога, которых не было в saved order.
     *
     * enabled-по-умолчанию для новых режимов: true, если {@code supported}
     * содержит код; иначе false.
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
     * Дефолт при первом запуске (до получения списка supported из SDK).
     * По умолчанию ничего не enabled — заполнится в {@link #mergeWithSupported}.
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
