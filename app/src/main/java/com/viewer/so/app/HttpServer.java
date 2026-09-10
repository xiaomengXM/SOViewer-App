package com.viewer.so.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置本地 HTTP 服务器。
 *
 * 移植说明：原实现内嵌于 Core.java（751-932 行），仅依赖 PluginContext 的
 * getPreferences()，此处改为 App.prefs()，**业务逻辑零改动**。
 *
 * 承担职责：
 *  - 绕开 Android WebView 加载本地文件的跨域限制（核心设计动机）
 *  - 为预览页提供 /preview/{token}、/raw/{token} 流式文件读取（规避大文件 OOM）
 *  - 提供 /cache/、/proxy/、/api/preload、/api/save_file 等脚本能力端点
 */
public final class HttpServer {

    private HttpServer() {}

    static ServerSocket currentServerSocket;
    static volatile int currentPort = 0;
    static volatile boolean isStartingServer = false;

    private static final ExecutorService serverExecutor = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 原签名保留：startIfNeeded(context, Runnable)，context 参数已不再需要，保留以兼容调用点 */
    public static synchronized void startIfNeeded(Context context, Runnable onStarted) {
        if (currentServerSocket != null && !currentServerSocket.isClosed() && currentPort > 0) {
            if (onStarted != null) postToMainThread(onStarted);
            return;
        }
        if (isStartingServer) {
            postToMainThreadDelayed(() -> startIfNeeded(context, onStarted), 500);
            return;
        }
        isStartingServer = true;
        serverExecutor.submit(() -> {
            try {
                // 端口偏好：用户可在设置指定 1024-65535 的端口；留空或非法时回退默认扫描
                int portPref = Prefs.getInt(Consts.PREF_KEY_SERVER_PORT_HINT, 0, 0, 65535);
                int portToTry = portPref > 0 ? portPref : Consts.SERVER_PORT_SCAN_START;
                ServerSocket socket = null;
                if (portPref > 0) {
                    try {
                        socket = new ServerSocket(portPref, 50, InetAddress.getByName(Consts.LOCALHOST));
                    } catch (IOException e) {
                        socket = null; // 端口占用，回退扫描
                    }
                }
                while (socket == null && portToTry < Consts.SERVER_PORT_SECONDARY) {
                    try {
                        socket = new ServerSocket(portToTry, 50, InetAddress.getByName(Consts.LOCALHOST));
                    } catch (IOException e) {
                        portToTry++;
                    }
                }
                // 扫描区间也失败则让系统随机分配
                if (socket == null) {
                    socket = new ServerSocket(0, 50, InetAddress.getByName(Consts.LOCALHOST));
                }
                currentServerSocket = socket;
                currentPort = socket.getLocalPort();
            } catch (Exception e) {
                isStartingServer = false;
                return;
            } finally {
                isStartingServer = false;
            }
            if (onStarted != null) MAIN.post(onStarted);
            try {
                currentServerSocket.setSoTimeout(30000);
                boolean autoCleanup = Prefs.getBoolean(Consts.PREF_KEY_AUTO_CLEANUP_SESSIONS, true);
                while (!currentServerSocket.isClosed()) {
                    try {
                        Socket client = currentServerSocket.accept();
                        serverExecutor.submit(() -> handleClient(client));
                    } catch (SocketTimeoutException ste) {
                        // accept 超时属于正常空闲，顺带清理过期会话与临时文件
                        if (autoCleanup) PreviewEngine.cleanupExpiredSessions();
                        continue;
                    } catch (IOException ignored) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            } finally {
                synchronized (HttpServer.class) {
                    Utils.closeQuietly(currentServerSocket);
                    currentServerSocket = null;
                    currentPort = 0;
                }
            }
        });
    }

    public static void stopServer() {
        synchronized (HttpServer.class) {
            Utils.closeQuietly(currentServerSocket);
            currentServerSocket = null;
            currentPort = 0;
        }
    }

    private static void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            socket.setSoTimeout(5000);
            String line = Utils.readLineFromStream(in);
            if (line == null) return;
            String[] parts = line.trim().split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String rawTarget = parts.length > 1 ? parts[1] : "/";

            Map<String, String> headers = new LinkedHashMap<>();
            int headerCount = 0;
            while (true) {
                String h = Utils.readLineFromStream(in);
                if (h == null || h.trim().isEmpty() || ++headerCount > 100) break;
                int idx = h.indexOf(':');
                if (idx > 0) {
                    headers.put(h.substring(0, idx).trim().toLowerCase(), h.substring(idx + 1).trim());
                }
            }

            String path = rawTarget;
            int qIdx = path.indexOf('?');
            if (qIdx != -1) path = path.substring(0, qIdx);
            try {
                path = JsonUtils.decodeUrlComponent(path);
            } catch (Exception ignored) {}

            HttpRequest req = new HttpRequest(method, path, rawTarget, headers, in);
            HttpResponse res = new HttpResponse(out);

            if (path.startsWith("/raw/")) {
                handleRaw(req, res);
            } else if (path.startsWith("/api/preload")) {
                CacheManager.handlePreload(req, res);
            } else if (path.startsWith("/api/save_file")) {
                CacheManager.handleSaveFile(req, res);
            } else if (path.startsWith("/cache/")) {
                CacheManager.handleServeCache(req, res);
            } else if (path.startsWith("/preview/")) {
                handlePreview(req, res);
            } else if (path.startsWith("/proxy")) {
                ProxyService.handleProxy(req, res);
            } else {
                res.sendText(404, "Not Found", "Not Found");
            }
        } catch (Exception ignored) {
        } finally {
            Utils.closeQuietly(socket);
        }
    }

    /** 流式返回原始文件内容，不整块读入内存（大文件安全） */
    private static void handleRaw(HttpRequest req, HttpResponse res) throws IOException {
        PreviewEngine.PreviewSession session = PreviewEngine.getSession(req.path.substring(5));
        if (session == null || session.file == null || !session.file.exists()) {
            res.sendText(404, "Not Found", "File not found");
            return;
        }
        res.sendFile(session.file, "application/octet-stream");
    }

    private static void handlePreview(HttpRequest req, HttpResponse res) throws IOException {
        PreviewEngine.PreviewSession session = PreviewEngine.getSession(req.path.substring(9));
        if (session == null || session.html == null) {
            res.sendText(404, "Not Found", "Page not found");
            return;
        }
        res.sendHtml(session.html);
    }

    private static Handler mainHandler() {
        return MAIN;
    }

    static void postToMainThread(Runnable r) {
        MAIN.post(r);
    }

    static void postToMainThreadDelayed(Runnable r, long delayMs) {
        MAIN.postDelayed(r, delayMs);
    }

    // ============================================================
    // HTTP 请求 / 响应封装（原 Core.java 873-932 行，纯 JDK 实现）
    // ============================================================

    public static final class HttpRequest {
        public final String method;
        public final String path;
        public final String rawTarget;
        public final Map<String, String> headers;
        public final InputStream in;

        HttpRequest(String method, String path, String rawTarget, Map<String, String> headers, InputStream in) {
            this.method = method;
            this.path = path;
            this.rawTarget = rawTarget;
            this.headers = headers;
            this.in = in;
        }

        public String header(String name) {
            return headers.get(name == null ? null : name.toLowerCase());
        }
    }

    public static final class HttpResponse {
        final OutputStream out;

        HttpResponse(OutputStream out) {
            this.out = out;
        }

        private void writeHeader(int code, String message, String contentType, String extraHeaders) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(message).append("\r\n");
            if (contentType != null) {
                sb.append("Content-Type: ").append(contentType).append("\r\n");
            }
            sb.append(Consts.CORS_EXPOSE_HEADERS);
            if (extraHeaders != null) sb.append(extraHeaders);
            sb.append("Connection: close\r\n\r\n");
            out.write(sb.toString().getBytes("UTF-8"));
        }

        public void sendText(int code, String message, String text) throws IOException {
            byte[] body = text == null ? new byte[0] : text.getBytes("UTF-8");
            writeHeader(code, message, "text/plain; charset=utf-8",
                    "Content-Length: " + body.length + "\r\n");
            out.write(body);
            out.flush();
        }

        public void sendHtml(String html) throws IOException {
            byte[] body = html == null ? new byte[0] : html.getBytes("UTF-8");
            writeHeader(200, "OK", "text/html; charset=utf-8",
                    "Content-Length: " + body.length + "\r\n");
            out.write(body);
            out.flush();
        }

        public void sendStream(InputStream is, String contentType, String disposition) throws IOException {
            StringBuilder extra = new StringBuilder();
            if (disposition != null) extra.append("Content-Disposition: ").append(disposition).append("\r\n");
            writeHeader(200, "OK", contentType, extra.toString());
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }

        /** 流式返回文件，按块输出，避免大文件占用内存 */
        public void sendFile(java.io.File file, String contentType) throws IOException {
            long length = file.length();
            writeHeader(200, "OK", contentType,
                    "Content-Length: " + length + "\r\n" +
                    "Accept-Ranges: none\r\n");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            out.flush();
        }
    }
}