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

package dezz.monjaro.drive_modes.util;

import android.util.Log;

public final class Logs {

    public static final String TAG = "MonjaroDriveModes";

    private Logs() {}

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String msg, Throwable t) {
        Log.d(TAG, msg, t);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void w(String msg, Throwable t) {
        Log.w(TAG, msg, t);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }
}
