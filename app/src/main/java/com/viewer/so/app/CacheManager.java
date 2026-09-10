package com.viewer.so.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 缓存与预加载管理器。从 Core.java（1089-1442 行）移植。
 *
 * 改动：
 *  - PluginContext → App.ctx() / Prefs
 *  - getFilesDir() → Utils.webCacheDir()
 *  - handleSaveFile 的 HTTP 内联参数 → HttpServer.HttpRequest/HttpResponse
 */
public final class CacheManager {

    private CacheManager() {}

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /** 匹配脚本中的远程资源引用（src= / href= / import 等） */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:src|href|url)\\s*[=:(]\\s*['\"]?(https?://[^'\"\\s)>]+)['\"]?",
            Pattern.CASE_INSENSITIVE);

    // ============================================================
    // 预加载结果
    // ============================================================

    public static final class PreloadResult {
        public String processedCode;
        public JSONArray preloadUrls = new JSONArray();
    }

    /** 提取脚本中引用的远程资源，生成预加载清单（不改写脚本内容） */
    public static PreloadResult processScriptResources(String scriptCode) {
        PreloadResult result = new PreloadResult();
        result.processedCode = scriptCode == null ? "" : scriptCode;
        if (scriptCode == null || scriptCode.isEmpty()) return result;

        try {
            Set<String> seen = new LinkedHashSet<>();
            Matcher m = URL_PATTERN.matcher(scriptCode);
            while (m.find()) {
                String url = m.group(1);
                if (url == null || url.isEmpty()) continue;
                if (!seen.add(url)) continue;

                JSONObject obj = new JSONObject();
                obj.put("original", url);
                obj.put("local", buildLocalCacheFileName(url, guessName(url)));
                obj.put("name", guessName(url));
                obj.put("minifiedPreferred", true);
                obj.put("mergedMinified", false);
                JSONArray cands = new JSONArray();
                cands.put(url);
                obj.put("candidates", cands);
                result.preloadUrls.put(obj);
            }
        } catch (Throwable ignored) {
            // 解析失败则退化为无预加载
        }
        return result;
    }

    /** 去掉重复的本地缓存 URL 引用，避免重复加载 */
    public static String removeRedundantCachedLocalUrls(String code) {
        if (code == null) return "";
        // 原实现用于去除已改为本地路径的重复引用；此处保留最小实现
        return code;
    }

    /** 依据 URL 生成稳定的本地缓存文件名 */
    public static String buildLocalCacheFileName(String url, String name) {
        String base = (name == null || name.isEmpty()) ? "res" : name;
        // 用 URL 摘要做前缀，避免同名资源互相覆盖
        String hash = shortHash(url);
        return hash + "_" + base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest((s == null ? "" : s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && i < d.length; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "res" + Math.abs(s == null ? 0 : s.hashCode());
        }
    }

    private static String guessName(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            String path = u.getPath();
            if (path != null && !path.isEmpty()) {
                int slash = path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                if (!name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {}
        return "resource";
    }

    // ============================================================
    // 缓存目录维护
    // ============================================================

    /** 裁剪缓存到设定上限（LRU：按最后修改时间由旧到新删除） */
    public static void trimCacheToLimit() {
        try {
            File dir = Utils.webCacheDir();
            if (!dir.exists()) return;
            long maxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_CACHE_MAX_MB,
                    Consts.DEFAULT_CACHE_MAX_MB, 1, 4096);
            long size = Utils.dirSize(dir);
            if (size <= maxBytes) return;

            File[] files = dir.listFiles();
            if (files == null) return;
            java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : files) {
                if (size <= maxBytes) break;
                long len = f.length();
                if (f.delete()) size -= len;
            }
        } catch (Throwable ignored) {}
    }

    public static void clearCache() {
        Utils.deleteRecursive(Utils.webCacheDir());
    }

    // ============================================================
    // HTTP 端点实现
    // ============================================================

    /** 预加载：把远程资源下载到本地缓存并返回映射（/api/preload） */
    public static void handlePreload(HttpServer.HttpRequest req, HttpServer.HttpResponse res) throws java.io.IOException {
        // 需要有效会话
        String token = extractToken(req);
        if (Utils.isEmpty(token) || PreviewEngine.getSession(token) == null) {
            res.sendText(403, "Forbidden", "Valid preview session required");
            return;
        }

        // 支持 POST body 传入待下载清单
        String body = "";
        if ("POST".equalsIgnoreCase(req.method) && req.in != null) {
            int len = 0;
            String cl = req.header("content-length");
            if (cl != null) {
                try {
                    len = Integer.parseInt(cl.trim());
                } catch (Throwable ignored) {}
            }
            // 防御：限制预加载请求体大小
            long maxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_PRELOAD_MAX_MB,
                    Consts.DEFAULT_PRELOAD_MAX_MB, 1, 512);
            if (len > 0 && len <= maxBytes) {
                body = new String(Utils.readExact(req.in, len), "UTF-8");
            }
        }

        JSONArray results = new JSONArray();
        JSONArray candidates = JsonUtils.parseArray(Utils.isEmpty(body) ? "[]" : body);

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject cand = candidates.optJSONObject(i);
            if (cand == null) continue;
            String url = JsonUtils.optString(cand, "original", "");
            String localName = JsonUtils.optString(cand, "local", "");
            if (Utils.isEmpty(url) || Utils.isEmpty(localName)) continue;

            JSONObject r = new JSONObject();
            try {
                r.put("original", url);
                r.put("local", localName);

                File out = new File(Utils.webCacheDir(), localName);
                boolean ok = downloadToFile(url, out);
                r.put("status", ok ? "ok" : "fail");
                r.put("size", out.length());
            } catch (Throwable t) {
                try {
                    r.put("status", "fail");
                    r.put("reason", String.valueOf(t.getMessage()));
                } catch (Throwable ignored) {}
            }
            results.put(r);
        }

        res.sendText(200, "OK", results.toString());
    }

    /** 下载远程资源到本地文件 */
    public static boolean downloadToFile(String url, File out) {
        try {
            if (out.exists() && out.length() > 0) return true;
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) SOViewerApp")
                    .build();
            try (Response resp = HTTP.newCall(request).execute()) {
                if (!resp.isSuccessful()) return false;
                ResponseBody body = resp.body();
                if (body == null) return false;

                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (InputStream is = body.byteStream();
                     OutputStream os = new FileOutputStream(out)) {
                    Utils.copyStream(is, os);
                }
            }
            return out.exists() && out.length() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 提供本地缓存文件（/cache/xxx） */
    public static void handleServeCache(HttpServer.HttpRequest req, HttpServer.HttpResponse res) throws java.io.IOException {
        String name = req.path.substring("/cache/".length());
        if (Utils.isEmpty(name) || name.contains("..") || name.contains("/")) {
            res.sendText(400, "Bad Request", "Invalid cache path");
            return;
        }
        File f = new File(Utils.webCacheDir(), name);
        if (!f.exists() || !f.isFile()) {
            res.sendText(404, "Not Found", "Cache miss");
            return;
        }
        res.sendFile(f, guessContentType(name));
    }

    /** 脚本保存文件端点（/api/save_file），需 trusted 会话 */
    public static void handleSaveFile(HttpServer.HttpRequest req, HttpServer.HttpResponse res) throws java.io.IOException {
        String token = extractToken(req);
        PreviewEngine.PreviewSession session = PreviewEngine.getSession(token);
        if (session == null) {
            res.sendText(403, "Forbidden", "Valid preview session required");
            return;
        }
        // 依赖 trusted 授权 + 全局开关
        boolean saveEnabled = session.trusted
                && Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_SAVE_ENABLED, true);
        if (!saveEnabled) {
            res.sendText(403, "Forbidden", "Save permission denied");
            return;
        }

        if (!"POST".equalsIgnoreCase(req.method) || req.in == null) {
            res.sendText(405, "Method Not Allowed", "POST required");
            return;
        }

        int len = 0;
        String cl = req.header("content-length");
        if (cl != null) {
            try {
                len = Integer.parseInt(cl.trim());
            } catch (Throwable ignored) {}
        }

        long maxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_SAVE_MAX_MB,
                Consts.DEFAULT_SAVE_MAX_MB, 1, 512);
        if (len <= 0) {
            res.sendText(400, "Bad Request", "Empty body");
            return;
        }
        if (len > maxBytes) {
            res.sendText(413, "Payload Too Large", "Exceeds save limit");
            return;
        }

        String fileName = JsonUtils.parseQuery(extractQuery(req)).getOrDefault("name", "saved_" + System.currentTimeMillis());
        String safeName = Utils.sanitizeFileName(fileName);

        try {
            byte[] data = Utils.readExact(req.in, len);
            File dir = Utils.exportsDir();
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, safeName);
            Utils.writeFile(out, data);

            JSONObject ok = new JSONObject();
            try {
                ok.put("status", "ok");
                ok.put("path", out.getAbsolutePath());
                ok.put("size", data.length);
            } catch (Throwable ignored) {}
            res.sendText(200, "OK", ok.toString());
        } catch (Throwable t) {
            res.sendText(500, "Internal Error", "Save failed: " + t.getMessage());
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private static String extractToken(HttpServer.HttpRequest req) {
        String h = req.header("x-so-viewer-token");
        if (!Utils.isEmpty(h)) return h.trim();
        String q = extractQuery(req);
        if (!Utils.isEmpty(q)) {
            return JsonUtils.parseQuery(q).getOrDefault("token", "");
        }
        // 支持 /proxy/{token}/... 形式
        if (req.path != null && req.path.startsWith("/proxy/")) {
            String rest = req.path.substring(7);
            int slash = rest.indexOf('/');
            String t = slash >= 0 ? rest.substring(0, slash) : rest;
            if (!t.matches("(?i)^https?:$")) return t;
        }
        return "";
    }

    private static String extractQuery(HttpServer.HttpRequest req) {
        String raw = req.rawTarget == null ? "" : req.rawTarget;
        int i = raw.indexOf('?');
        return i >= 0 ? raw.substring(i + 1) : "";
    }

    public static String guessContentType(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}