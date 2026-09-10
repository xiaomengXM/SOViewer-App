package com.viewer.so.app;

import android.content.SharedPreferences;

/**
 * 偏好读写封装。替代原代码中反复出现的
 * getIntPref(prefs, key, def, min, max) / getMbPrefBytes(...) 等工具方法。
 */
public final class Prefs {

    private Prefs() {}

    private static SharedPreferences sp() {
        return App.prefs();
    }

    // ---------- 基础读写 ----------

    public static String getString(String key, String def) {
        try {
            String v = sp().getString(key, def);
            return v == null ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean getBoolean(String key, boolean def) {
        try {
            return sp().getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static int getInt(String key, int def, int min, int max) {
        try {
            String raw = sp().getString(key, null);
            int v;
            if (raw == null || raw.trim().isEmpty()) {
                v = def;
            } else {
                v = Integer.parseInt(raw.trim());
            }
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return def;
        }
    }

    public static long getLong(String key, long def, long min, long max) {
        try {
            String raw = sp().getString(key, null);
            long v = (raw == null || raw.trim().isEmpty()) ? def : Long.parseLong(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return def;
        }
    }

    /** 以 MB 为单位存储、返回字节数（对应原 getMbPrefBytes） */
    public static long getMbPrefBytes(String key, int defMb, int min, int max) {
        int mb = getInt(key, defMb, min, max);
        return mb * 1024L * 1024L;
    }

    public static void putString(String key, String value) {
        try {
            sp().edit().putString(key, value).apply();
        } catch (Throwable ignored) {}
    }

    public static void putBoolean(String key, boolean value) {
        try {
            sp().edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {}
    }

    public static void putInt(String key, int value) {
        try {
            sp().edit().putString(key, String.valueOf(value)).apply();
        } catch (Throwable ignored) {}
    }

    public static void remove(String key) {
        try {
            sp().edit().remove(key).apply();
        } catch (Throwable ignored) {}
    }

    public static SharedPreferences.Editor edit() {
        return sp().edit();
    }
}