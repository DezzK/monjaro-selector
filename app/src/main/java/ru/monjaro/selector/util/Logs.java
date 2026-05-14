package ru.monjaro.selector.util;

import android.util.Log;

public final class Logs {

    public static final String TAG = "MonjaroSelector";

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
