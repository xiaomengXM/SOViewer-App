package com.viewer.so.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 工具层。
 * 原实现依赖 bin.mt.json（MT 私有库），此处统一改走 org.json，
 * 并保留原 Utils 中静态签名（optString/optBoolean/...），使调用点零改动。
 */
public final class JsonUtils {

    private JsonUtils() {}

    // ============ 安全取值（对齐 bin.mt.json 语义） ============

    public static String optString(JSONObject obj, String key, String def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key) || obj.isNull(key)) return def;
            Object v = obj.get(key);
            if (v instanceof String) return (String) v;
            if (v == null) return def;
            return String.valueOf(v);
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean optBoolean(JSONObject obj, String key, boolean def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key) || obj.isNull(key)) return def;
            Object v = obj.get(key);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            if (v instanceof String) {
                String s = ((String) v).trim();
                if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
                if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
                return def;
            }
            return def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static int optInt(JSONObject obj, String key, int def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key) || obj.isNull(key)) return def;
            Object v = obj.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) return Integer.parseInt(((String) v).trim());
            return def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static long optLong(JSONObject obj, String key, long def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key) || obj.isNull(key)) return def;
            Object v = obj.get(key);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) return Long.parseLong(((String) v).trim());
            return def;
        } catch (Throwable t) {
            return def;
        }
    }

    // ============ 数组解析 ============

    /** 从字符串安全解析 JSONArray，失败返回空数组 */
    public static JSONArray parseArray(String json) {
        if (json == null || json.trim().isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    /** 从字符串安全解析 JSONObject，失败返回空对象 */
    public static JSONObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(json);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    // ============ 字符串转义 ============

    /** 转义为可嵌入 JS 双引号字符串的内容 */
    public static String escapeJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\'': sb.append("\\'");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '<':
                    // 防止 </script> 截断
                    if (i + 6 < s.length() && s.regionMatches(true, i, "</scr", 0, 5)) {
                        sb.append("\\x3C");
                    } else {
                        sb.append(c);
                    }
                    break;
                case '\u2028':
                case '\u2029':
                    sb.append(String.format("\\u%04x", (int) c));
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 转义为可嵌入 JS 单引号字符串的内容 */
    public static String escapeJsSingleQuoted(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\u2028':
                case '\u2029':
                    sb.append(String.format("\\u%04x", (int) c));
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // ============ 其他工具 ============

    /** 取文件扩展名（小写，不含点） */
    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** 解析 URL query，与原 Utils.parseQuery 等价 */
    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) {
                    map.put(decodeUrlComponent(pair), "");
                } else {
                    String k = decodeUrlComponent(pair.substring(0, eq));
                    String v = decodeUrlComponent(pair.substring(eq + 1));
                    map.put(k, v);
                }
            } catch (Throwable ignored) {
                // 忽略畸形参数
            }
        }
        return map;
    }

    public static String decodeUrlComponent(String s) {
        if (s == null) return "";
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    public static Map<String, String> emptyMap() {
        return Collections.emptyMap();
    }
}