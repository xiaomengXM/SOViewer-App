package com.viewer.so.app;

import android.app.Application;
import android.util.Log;

/**
 * 应用入口。负责初始化全局 Context、内置脚本，以及预热本地服务器。
 */
public class SOViewerApplication extends Application {

    private static final String TAG = "SOViewer";

    @Override
    public void onCreate() {
        super.onCreate();
        App.init(this);

        try {
            // 首次启动或版本升级时写入内置预览脚本
            PreviewEngine.ensureDefaultScripts(App.prefs());

            // 启动本地 HTTP Server（预览链路的基础设施）
            HttpServer.startIfNeeded(this, null);

            // 清理过期缓存
            CacheManager.trimCacheToLimit();
        } catch (Throwable t) {
            Log.e(TAG, "init failed", t);
        }
    }
}