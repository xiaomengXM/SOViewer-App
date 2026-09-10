package com.viewer.so.app;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 通用 IO / 字符串工具。对应原 Core.java 内 Utils 内部类。
 */
public final class Utils {

    private Utils() {}

    // ---------- 流操作 ----------

    public static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {}
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    public static String readStreamToString(InputStream is) throws IOException {
        if (is == null) return null;
        try (InputStream in = is) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            copyStream(in, bos);
            return bos.toString("UTF-8");
        }
    }

    public static byte[] readFileBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(f.length(), 1 << 20));
            copyStream(in, bos);
            return bos.toByteArray();
        }
    }

    /**
     * 从流中读取一行（CRLF 或 LF 结尾），用于轻量 HTTP 解析。
     * 返回 null 表示流已结束。
     */
    public static String readLineFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        int b;
        boolean got = false;
        while ((b = in.read()) != -1) {
            got = true;
            if (b == '\n') break;
            if (b == '\r') continue;
            bos.write(b);
            // 防御：异常超长行直接截断
            if (bos.size() > 16384) break;
        }
        if (!got) return null;
        return bos.toString("UTF-8");
    }

    /**
     * 从流中按 Content-Length 读取指定字节数。
     */
    public static byte[] readExact(InputStream in, int length) throws IOException {
        if (length <= 0) return new byte[0];
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int r = in.read(data, off, length - off);
            if (r == -1) break;
            off += r;
        }
        if (off == length) return data;
        byte[] trimmed = new byte[off];
        System.arraycopy(data, 0, trimmed, 0, off);
        return trimmed;
    }

    public static void writeFile(File dest, byte[] data) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream os = new FileOutputStream(dest)) {
            os.write(data);
            os.flush();
        }
    }

    // ---------- 目录 ----------

    public static File filesDir() {
        return App.ctx().getFilesDir();
    }

    public static File webCacheDir() {
        return new File(filesDir(), "web_cache");
    }

    public static File tempReceivedDir() {
        return new File(filesDir(), "temp_received");
    }

    public static File exportsDir() {
        return new File(filesDir(), "exports");
    }

    /** 递归删除目录内容，返回删除的字节估算 */
    public static long deleteRecursive(File f) {
        if (f == null || !f.exists()) return 0;
        long total = 0;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    total += deleteRecursive(c);
                }
            }
        } else {
            total += f.length();
            f.delete();
        }
        return total;
    }

    /** 目录总大小 */
    public static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long total = 0;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File c : children) total += dirSize(c);
            }
        } else {
            total = dir.length();
        }
        return total;
    }

    public static int countFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] children = dir.listFiles();
        return children == null ? 0 : children.length;
    }

    // ---------- 杂项 ----------

    /** 文件名安全化，防止路径穿越 */
    public static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "unnamed";
        String s = name.replace('\\', '_').replace('/', '_');
        s = s.replace("..", "_");
        return s;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}