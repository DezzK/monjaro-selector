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

package dezz.monjaro.drive_modes.car;

import androidx.annotation.Nullable;

import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dezz.monjaro.drive_modes.R;

public final class DriveModeCatalog {

    private static final List<DriveModeDescriptor> ALL;
    private static final Map<Integer, DriveModeDescriptor> BY_CODE;

    static {
        List<DriveModeDescriptor> list = new ArrayList<>();
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_COMFORT,
                "comfort", R.string.mode_comfort, R.drawable.ic_mode_comfort, R.color.accent_comfort, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_ECO,
                "eco", R.string.mode_eco, R.drawable.ic_mode_eco, R.color.accent_eco, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_ECO_PLUS,
                "eco_plus", R.string.mode_eco_plus, R.drawable.ic_mode_eco, R.color.accent_eco, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_DYNAMIC,
                "dynamic", R.string.mode_dynamic, R.drawable.ic_mode_sport, R.color.accent_sport, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SPORT_PLUS,
                "sport_plus", R.string.mode_sport_plus, R.drawable.ic_mode_sport, R.color.accent_sport, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_NORMAL,
                "normal", R.string.mode_normal, R.drawable.ic_mode_generic, R.color.accent_neutral));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_SNOW,
                "snow", R.string.mode_snow, R.drawable.ic_mode_snow, R.color.accent_snow, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_OFFROAD,
                "offroad", R.string.mode_offroad, R.drawable.ic_mode_offroad, R.color.accent_offroad, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_MUD,
                "mud", R.string.mode_mud, R.drawable.ic_mode_mud, R.color.accent_mud));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_SAND,
                "sand", R.string.mode_sand, R.drawable.ic_mode_sand, R.color.accent_sand, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_ROCK,
                "rock", R.string.mode_rock, R.drawable.ic_mode_rock, R.color.accent_rock));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_POWER,
                "power", R.string.mode_power, R.drawable.ic_mode_power, R.color.accent_power));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_HYBRID,
                "hybrid", R.string.mode_hybrid, R.drawable.ic_mode_hybrid, R.color.accent_hybrid));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_PURE,
                "pure", R.string.mode_pure, R.drawable.ic_mode_pure, R.color.accent_pure));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_AWD,
                "awd", R.string.mode_awd, R.drawable.ic_mode_awd, R.color.accent_awd));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_eAWD,
                "eawd", R.string.mode_eawd, R.drawable.ic_mode_awd, R.color.accent_awd));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_CUSTOM,
                "custom", R.string.mode_custom, R.drawable.ic_mode_custom, R.color.accent_custom));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_ADAPTIVE,
                "adaptive", R.string.mode_adaptive, R.drawable.ic_mode_adaptive, R.color.accent_neutral, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_HDC,
                "hdc", R.string.mode_hdc, R.drawable.ic_mode_hdc, R.color.accent_offroad));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_PHEV,
                "phev", R.string.mode_phev, R.drawable.ic_mode_phev, R.color.accent_pure));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_ECO_HEV_PHEV,
                "eco_hev_phev", R.string.mode_eco_hev_phev, R.drawable.ic_mode_eco, R.color.accent_eco, true));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_SAVE,
                "save", R.string.mode_save, R.drawable.ic_mode_save, R.color.accent_neutral));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_XC,
                "xc", R.string.mode_xc, R.drawable.ic_mode_xc, R.color.accent_neutral));
        list.add(new DriveModeDescriptor(IDriveMode.DRIVE_MODE_SELECTION_UNKNOWN,
                "unknown", R.string.mode_unknown, R.drawable.ic_mode_generic, R.color.accent_neutral));
        ALL = Collections.unmodifiableList(list);

        Map<Integer, DriveModeDescriptor> map = new HashMap<>();
        for (DriveModeDescriptor d : list) {
            map.put(d.code, d);
        }
        BY_CODE = Collections.unmodifiableMap(map);
    }

    private DriveModeCatalog() {}

    public static List<DriveModeDescriptor> all() {
        return ALL;
    }

    @Nullable
    public static DriveModeDescriptor byCode(int code) {
        return BY_CODE.get(code);
    }

    public static DriveModeDescriptor byCodeOrGeneric(int code) {
        DriveModeDescriptor d = BY_CODE.get(code);
        if (d != null) return d;
        return new DriveModeDescriptor(code, "code_" + code,
                R.string.mode_unknown, R.drawable.ic_mode_generic, R.color.accent_neutral);
    }

    /** Default order — when user hasn't set anything yet. */
    public static int[] defaultOrder() {
        return new int[] {
                IDriveMode.DRIVE_MODE_SELECTION_COMFORT,
                IDriveMode.DRIVE_MODE_SELECTION_ECO,
                IDriveMode.DRIVE_MODE_SELECTION_DYNAMIC,
                IDriveMode.DRIVE_MODE_SELECTION_SNOW,
                IDriveMode.DRIVE_MODE_SELECTION_OFFROAD,
        };
    }

}
