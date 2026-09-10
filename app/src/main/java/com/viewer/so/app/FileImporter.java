package com.viewer.so.app;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.webkit.MimeTypeMap;

/**
 * 外部文件导入辅助：解析显示名、类型。
 */
public final class FileImporter {

    private FileImporter() {}

    /** 通过 ContentResolver 查询文件名 */
    public static String queryDisplayName(Context ctx, Uri uri) {
        if (uri == null) return null;
        String result = null;

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = c.getString(idx);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) result = path.substring(cut + 1);
                else result = path;
            }
        }

        if (result == null) result = "unnamed";
        return result;
    }

    /** 根据文件名或 Uri 推断 MIME */
    public static String guessMime(Context ctx, Uri uri, String fileName) {
        if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
            String t = ctx.getContentResolver().getType(uri);
            if (t != null) return t;
        }
        String ext = JsonUtils.getExtension(fileName);
        if (!ext.isEmpty()) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "application/octet-stream";
    }
}