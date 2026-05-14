package ru.monjaro.selector.car;

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
