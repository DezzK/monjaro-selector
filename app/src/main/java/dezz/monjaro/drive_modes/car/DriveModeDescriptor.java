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

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public final class DriveModeDescriptor {

    public final int code;
    public final String key;
    @StringRes public final int labelRes;
    @DrawableRes public final int iconRes;
    @ColorRes public final int accentRes;

    public DriveModeDescriptor(int code, String key,
                               @StringRes int labelRes,
                               @DrawableRes int iconRes,
                               @ColorRes int accentRes) {
        this.code = code;
        this.key = key;
        this.labelRes = labelRes;
        this.iconRes = iconRes;
        this.accentRes = accentRes;
    }
}
