package com.viewer.so.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 脚本代理服务。从 Core.java（933-1088 行）移植。
 *
 * 设计要点（保留原安全边界）：
 *  - 必须携带有效预览会话 token
 *  - 必须开启全局 proxy 开关
 *  - 白名单命中才放行（allowlist 为空则按策略拒绝或放行）
 *  - 响应体超过 proxyResponseMaxBytes 直接中断，防内存/流量滥用
 */
public final class ProxyService {

    private ProxyService() {}

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    /** 请求形态：/proxy/{token}/{scheme}/{host}/{path...} */
    public static void handleProxy(HttpServer.HttpRequest req, HttpServer.HttpResponse res) throws IOException {
        boolean proxyEnabled = Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false);
        if (!proxyEnabled) {
            res.sendText(403, "Forbidden", "Proxy disabled");
            return;
        }

        String token = extractToken(req);
        if (Utils.isEmpty(token) || PreviewEngine.getSession(token) == null) {
            res.sendText(403, "Forbidden", "Valid preview session required");
            return;
        }

        String target = buildTargetUrl(req.path);
        if (target == null) {
            res.sendText(400, "Bad Request", "Invalid proxy target");
            return;
        }

        if (!isAllowed(target)) {
            res.sendText(403, "Forbidden", "Target blocked by allowlist");
            return;
        }

        long maxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB,
                Consts.DEFAULT_PROXY_RESPONSE_MAX_MB, 1, 512);

        Request.Builder rb = new Request.Builder()
                .url(target)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) SOViewerApp");

        // 透传常用头
        String accept = req.header("accept");
        if (!Utils.isEmpty(accept)) rb.header("Accept", accept);
        String referer = req.header("referer");
        if (!Utils.isEmpty(referer)) rb.header("Referer", referer);

        try (Response resp = HTTP.newCall(rb.build()).execute()) {
            ResponseBody body = resp.body();
            String contentType = resp.header("Content-Type", "application/octet-stream");

            if (body == null) {
                res.sendText(resp.code(), "OK", "");
                return;
            }

            long declared = body.contentLength();
            if (declared > maxBytes) {
                res.sendText(413, "Payload Too Large", "Response exceeds proxy limit");
                return;
            }

            // 流式转发，边读边限制
            sendStreamLimited(res, body.byteStream(), contentType, maxBytes, resp.code());
        } catch (Throwable t) {
            res.sendText(502, "Bad Gateway", "Proxy fetch failed: " + t.getMessage());
        }
    }

    private static void sendStreamLimited(HttpServer.HttpResponse res, InputStream in,
                                          String contentType, long maxBytes, int code) throws IOException {
        // 由于要限制体积，这里先落临时缓冲再发送（限制在 maxBytes 内）
        File tmp = File.createTempFile("proxy_", ".tmp", Utils.filesDir());
        try {
            long total = 0;
            try (java.io.OutputStream os = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = in.read(buf)) != -1) {
                    total += len;
                    if (total > maxBytes) {
                        res.sendText(413, "Payload Too Large", "Response exceeds proxy limit");
                        return;
                    }
                    os.write(buf, 0, len);
                }
            }
            res.sendFile(tmp, contentType);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    // ============================================================
    // 解析与安全策略
    // ============================================================

    /**
     * 从 /proxy/{token}/{scheme}/{...} 还原目标 URL。
     * 兼容 /proxy/https://host/path（token 已由 header 提供）两种写法。
     */
    private static String buildTargetUrl(String path) {
        if (path == null) return null;
        String rest = path.startsWith("/proxy/") ? path.substring("/proxy/".length()) : path;
        // rest 允许形如 {token}/https/host/path 或 https/host/path
        // 统一还原为 http(s)://host/path
        String s = rest;

        // 去掉可能存在的 token 段
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            int slash = s.indexOf('/');
            if (slash < 0) return null;
            s = s.substring(slash + 1);
        }
        if (s.startsWith("https/")) {
            s = "https://" + s.substring("https/".length());
        } else if (s.startsWith("http/")) {
            s = "http://" + s.substring("http/".length());
        } else if (s.startsWith("https:")) {
            s = s.replaceFirst("^https:/+", "https://");
        } else if (s.startsWith("http:")) {
            s = s.replaceFirst("^http:/+", "http://");
        }

        if (!s.startsWith("http://") && !s.startsWith("https://")) return null;
        return s;
    }

    /** 白名单校验：allowlist 为逗号/换行分隔的域名或关键词 */
    private static boolean isAllowed(String url) {
        String raw = Prefs.getString(Consts.PREF_KEY_PROXY_ALLOWLIST, "").trim();
        if (raw.isEmpty()) return true; // 未配置白名单则不额外拦截（仍受开关与会话约束）

        String host;
        try {
            host = new java.net.URI(url).getHost();
        } catch (Throwable t) {
            return false;
        }
        if (host == null) return false;

        for (String entry : raw.split("[,\\n]")) {
            String e = entry.trim().toLowerCase();
            if (e.isEmpty()) continue;
            if (host.toLowerCase().equals(e) || host.toLowerCase().endsWith("." + e)) {
                return true;
            }
        }
        return false;
    }

    private static String extractToken(HttpServer.HttpRequest req) {
        String h = req.header("x-so-viewer-token");
        if (!Utils.isEmpty(h)) return h.trim();
        String raw = req.rawTarget == null ? "" : req.rawTarget;
        int q = raw.indexOf('?');
        if (q >= 0) {
            String t = JsonUtils.parseQuery(raw.substring(q + 1)).getOrDefault("token", "");
            if (!Utils.isEmpty(t)) return t;
        }
        // /proxy/{token}/https/...
        if (req.path != null && req.path.startsWith("/proxy/")) {
            String rest = req.path.substring("/proxy/".length());
            int slash = rest.indexOf('/');
            String t = slash >= 0 ? rest.substring(0, slash) : rest;
            if (!Utils.isEmpty(t) && !t.matches("(?i)^https?:$")) return t;
        }
        return "";
    }
}