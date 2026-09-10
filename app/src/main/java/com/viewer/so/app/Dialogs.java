package com.viewer.so.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 对话框 / Toast 兼容层。
 * 替代原 PluginUI.buildDialog() 与 context.showToast()，
 * 保持同名链式调用，使业务代码零改动。
 */
public final class Dialogs {

    private Dialogs() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void toast(final String msg) {
        MAIN.post(() -> showToast(App.uiCtx(), msg, Toast.LENGTH_SHORT));
    }

    public static void toastLong(final String msg) {
        MAIN.post(() -> showToast(App.uiCtx(), msg, Toast.LENGTH_LONG));
    }

    private static void showToast(Context c, String msg, int len) {
        try {
            Toast.makeText(c, msg == null ? "" : msg, len).show();
        } catch (Throwable t) {
            // 兜底：Activity Context 失效时改用 Application Context
            try {
                Toast.makeText(App.ctx(), msg == null ? "" : msg, len).show();
            } catch (Throwable t2) {
                android.util.Log.e("SOViewer", "toast failed", t2);
            }
        }
    }

    /** 链式对话框构建器，对齐 PluginDialog 的常用 API */
    public static final class Builder {
        private final AlertDialog.Builder b;
        private String posText, negText, neuText;
        private DialogInterface.OnClickListener posL, negL, neuL;

        public Builder() {
            // 关键：必须用当前 Activity 的 Context。
            // Application Context 弹 Dialog 在 MIUI 等 ROM 上会抛 BadTokenException 而静默失败。
            Context c = App.uiCtx();
            if (c == null) c = App.ctx();
            b = new AlertDialog.Builder(c);
        }

        public Builder(Context themed) {
            b = new AlertDialog.Builder(themed);
        }

        public Builder setTitle(CharSequence title) {
            b.setTitle(title);
            return this;
        }

        public Builder setMessage(CharSequence msg) {
            b.setMessage(msg);
            return this;
        }

        public Builder setView(android.view.View v) {
            b.setView(v);
            return this;
        }

        public Builder setCancelable(boolean c) {
            b.setCancelable(c);
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            b.setItems(items, listener);
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.posText = text == null ? null : text.toString();
            this.posL = l;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.negText = text == null ? null : text.toString();
            this.negL = l;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.neuText = text == null ? null : text.toString();
            this.neuL = l;
            return this;
        }

        public AlertDialog create() {
            if (posText != null) b.setPositiveButton(posText, posL);
            if (negText != null) b.setNegativeButton(negText, negL);
            if (neuText != null) b.setNeutralButton(neuText, neuL);
            return b.create();
        }

        public void show() {
            MAIN.post(() -> {
                try {
                    create().show();
                } catch (Throwable t) {
                    android.util.Log.e("SOViewer", "dialog show failed", t);
                    // 兜底：Activity 已销毁时用 Application Context 再试一次
                    try {
                        new AlertDialog.Builder(App.ctx())
                                .setTitle("提示")
                                .setMessage("当前界面已不可用，请重试")
                                .setPositiveButton("好", null)
                                .create().show();
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    public static Builder build() {
        return new Builder();
    }
}