package com.viewer.so.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全局上下文持有 + 偏好封装。
 * 原插件通过 PluginContext 获取这些能力，现用 Application 级 Context 统一提供。
 */
public final class App {

    private static Context appContext;
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private App() {}

    public static void init(Context ctx) {
        if (appContext == null) {
            appContext = ctx.getApplicationContext();
        }
    }

    public static Context ctx() {
        return appContext;
    }

    public static ExecutorService io() {
        return IO;
    }

    /** 与原 context.getPreferences() 等价 */
    public static SharedPreferences prefs() {
        if (appContext == null) {
            throw new IllegalStateException("App not initialized");
        }
        return appContext.getSharedPreferences(Consts.PREFS_NAME, Context.MODE_PRIVATE);
    }
}