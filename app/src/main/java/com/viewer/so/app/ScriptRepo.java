package com.viewer.so.app;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 脚本仓库：对 PREF_KEY_SCRIPTS（JSON 数组）的增删改查。
 * 对应原 Core.java 中分散在 showScriptDialog / showEditDialog 里的持久化逻辑，
 * 抽出来便于设置页与业务层复用。
 */
public final class ScriptRepo {

    private ScriptRepo() {}

    /** 读取全部脚本 */
    public static JSONArray all(SharedPreferences sp) {
        return Utils.readJsonArray(sp, Consts.PREF_KEY_SCRIPTS);
    }

    public static JSONArray all() {
        return all(App.prefs());
    }

    /** 保存整个数组 */
    public static void save(SharedPreferences sp, JSONArray arr) {
        sp.edit().putString(Consts.PREF_KEY_SCRIPTS, arr.toString()).apply();
    }

    public static void save(JSONArray arr) {
        save(App.prefs(), arr);
    }

    /** 新增脚本，返回新数组 */
    public static JSONArray add(JSONObject script) {
        JSONArray arr = all();
        JSONArray fresh = new JSONArray();
        for (int i = 0; i < arr.length(); i++) fresh.put(arr.optJSONObject(i));
        fresh.put(script);
        save(fresh);
        return fresh;
    }

    /** 按索引替换 */
    public static void replaceAt(int idx, JSONObject script) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length()) return;
        JSONArray fresh = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            fresh.put(i == idx ? script : arr.optJSONObject(i));
        }
        save(fresh);
    }

    /** 按索引删除 */
    public static void removeAt(int idx) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length()) return;
        JSONArray fresh = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != idx) fresh.put(arr.optJSONObject(i));
        }
        save(fresh);
    }

    /** 交换两项位置 */
    public static void swap(int a, int b) {
        JSONArray arr = all();
        if (a < 0 || b < 0 || a >= arr.length() || b >= arr.length()) return;
        JSONArray fresh = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i == a) fresh.put(arr.optJSONObject(b));
            else if (i == b) fresh.put(arr.optJSONObject(a));
            else fresh.put(arr.optJSONObject(i));
        }
        save(fresh);
    }

    /** 上移 */
    public static boolean moveUp(int idx) {
        if (idx <= 0) return false;
        swap(idx, idx - 1);
        return true;
    }

    /** 下移 */
    public static boolean moveDown(int idx) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length() - 1) return false;
        swap(idx, idx + 1);
        return true;
    }

    /** 切换启用状态 */
    public static boolean toggleEnabled(int idx) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length()) return false;
        JSONObject o = arr.optJSONObject(idx);
        if (o == null) return false;
        boolean enabled = !PreviewEngine.isScriptEnabled(o);
        try {
            o.put("enabled", enabled);
        } catch (Throwable ignored) {}
        replaceAt(idx, o);
        return enabled;
    }

    /** 切换单脚本可信授权 */
    public static boolean toggleTrusted(int idx) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length()) return false;
        JSONObject o = arr.optJSONObject(idx);
        if (o == null) return false;
        boolean trusted = !PreviewEngine.isScriptTrusted(o);
        try {
            o.put("trusted", trusted);
        } catch (Throwable ignored) {}
        replaceAt(idx, o);
        return trusted;
    }

    /** 复制为新脚本 */
    public static void duplicate(int idx) {
        JSONArray arr = all();
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject src = arr.optJSONObject(idx);
        if (src == null) return;
        try {
            JSONObject copy = new JSONObject();
            copy.put("name", JsonUtils.optString(src, "name", "未命名脚本") + " 副本");
            copy.put("ext", JsonUtils.optString(src, "ext", ""));
            copy.put("code", JsonUtils.optString(src, "code", ""));
            copy.put("trusted", false);
            copy.put("enabled", true);
            JSONArray fresh = new JSONArray();
            for (int i = 0; i < arr.length(); i++) fresh.put(arr.optJSONObject(i));
            fresh.put(copy);
            save(fresh);
        } catch (Throwable ignored) {}
    }

    /** 统计：索引 0=内置数, 1=自定义数, 2=可信授权数, 3=启用数 */
    public static int[] count(JSONArray arr) {
        int[] c = new int[4];
        if (arr == null) return c;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (JsonUtils.optBoolean(o, "builtin", false)) c[0]++;
            else c[1]++;
            if (PreviewEngine.isScriptTrusted(o)) c[2]++;
            if (PreviewEngine.isScriptEnabled(o)) c[3]++;
        }
        return c;
    }

    /** 生成脚本状态摘要文本 */
    public static String summaryOf(boolean builtin, String ext, SharedPreferences sp, JSONObject script) {
        StringBuilder sb = new StringBuilder();
        sb.append(ext == null || ext.isEmpty() ? "无扩展名" : "." + ext.replace(",", " ."));
        sb.append(" · ");
        sb.append(builtin ? "内置" : "自定义");
        if (PreviewEngine.isScriptTrusted(script)) {
            sb.append(" · 已授权可信");
        }
        if (!PreviewEngine.isScriptEnabled(script)) {
            sb.append(" · 已停用");
        }
        return sb.toString();
    }
}
