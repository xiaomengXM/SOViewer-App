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

    /** 当前前台 Activity 的弱引用。用于弹 Dialog（Application Context 在部分 ROM 上弹不出窗）。 */
    private static java.lang.ref.WeakReference<android.app.Activity> currentActivity =
            new java.lang.ref.WeakReference<>(null);

    private App() {}

    public static void init(Context ctx) {
        if (appContext == null) {
            appContext = ctx.getApplicationContext();
            // 自动跟踪前台 Activity：所有 Activity 走 onResume/onPause 时更新引用
            try {
                ((android.app.Application) appContext)
                        .registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityResumed(android.app.Activity a) {
                        currentActivity = new java.lang.ref.WeakReference<>(a);
                    }
                    @Override public void onActivityPaused(android.app.Activity a) {
                        if (currentActivity.get() == a) {
                            currentActivity = new java.lang.ref.WeakReference<>(null);
                        }
                    }
                    @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
                    @Override public void onActivityStarted(android.app.Activity a) {}
                    @Override public void onActivityStopped(android.app.Activity a) {}
                    @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
                    @Override public void onActivityDestroyed(android.app.Activity a) {
                        if (currentActivity.get() == a) {
                            currentActivity = new java.lang.ref.WeakReference<>(null);
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    public static Context ctx() {
        return appContext;
    }

    /** 取当前 Activity，拿不到时回退到 Application Context。弹窗必须优先用它。 */
    public static Context uiCtx() {
        android.app.Activity a = currentActivity.get();
        return a != null && !a.isFinishing() ? a : appContext;
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