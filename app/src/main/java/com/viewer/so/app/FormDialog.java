package com.viewer.so.app;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单式对话框构建器。
 * 对应原 Core.java 的 ui.buildVerticalLayout().addTextView().addEditText() 链式写法，
 * 用原生 Layout 还原，保持移植后对话框的外观与交互一致。
 *
 * 用法：
 *   new FormDialog(this).title("脚本备份与导入").desc("...")
 *       .input("name", "名称", "默认值", "提示")
 *       .multiline("code", "脚本", "代码...", "JavaScript")
 *       .show("保存", values -&gt; { ... }, "取消", null);
 */
public final class FormDialog {

    private final Context ctx;
    private final List<Field> fields = new ArrayList<>();
    private String title;
    private String desc;
    private boolean cancelable = true;

    /** 表单字段 */
    private static final class Field {
        String key;
        String label;
        String value;
        String hint;
        boolean multiline;
        boolean password;
        boolean isNote;      // 纯文本说明
        EditText view;       // 输入框实例

        static Field input(String key, String label, String value, String hint,
                           boolean multiline, boolean password) {
            Field f = new Field();
            f.key = key;
            f.label = label;
            f.value = value;
            f.hint = hint;
            f.multiline = multiline;
            f.password = password;
            return f;
        }

        static Field note(String label, String text) {
            Field f = new Field();
            f.label = label;
            f.value = text;
            f.isNote = true;
            return f;
        }
    }

    public FormDialog(Context ctx) {
        this.ctx = ctx;
    }

    public FormDialog title(String t) { this.title = t; return this; }
    public FormDialog desc(String d) { this.desc = d; return this; }
    public FormDialog cancelable(boolean c) { this.cancelable = c; return this; }

    public FormDialog input(String key, String label, String value, String hint) {
        fields.add(Field.input(key, label, value, hint, false, false));
        return this;
    }

    public FormDialog multiline(String key, String label, String value, String hint) {
        fields.add(Field.input(key, label, value, hint, true, false));
        return this;
    }

    public FormDialog password(String key, String label, String value, String hint) {
        fields.add(Field.input(key, label, value, hint, false, true));
        return this;
    }

    /** 插入一段只读说明文字 */
    public FormDialog note(String label, String text) {
        fields.add(Field.note(label, text));
        return this;
    }

    // ============================================================
    // 视图构建
    // ============================================================

    private View buildView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, dp(10), pad, dp(4));

        if (desc != null) {
            TextView d = new TextView(ctx);
            d.setText(desc);
            d.setTextSize(13);
            d.setAlpha(0.75f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            root.addView(d, lp);
        }

        for (Field f : fields) {
            // 字段标签（说明项自带标题时也显示）
            if (f.label != null) {
                TextView label = new TextView(ctx);
                label.setText(f.label);
                label.setTextSize(14);
                label.setAlpha(0.9f);
                LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpl.topMargin = dp(10);
                root.addView(label, lpl);
            }

            if (f.isNote) {
                TextView tv = new TextView(ctx);
                tv.setText(f.value == null ? "" : f.value);
                tv.setTextSize(13);
                tv.setAlpha(0.7f);
                tv.setLineSpacing(0f, 1.2f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                root.addView(tv, lp);
                continue;
            }

            EditText et = new EditText(ctx);
            et.setText(f.value == null ? "" : f.value);
            if (f.hint != null) et.setHint(f.hint);
            if (f.multiline) {
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                et.setMinLines(6);
                et.setGravity(Gravity.TOP | Gravity.START);
            } else if (f.password) {
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                et.setSingleLine(true);
            } else {
                et.setInputType(InputType.TYPE_CLASS_TEXT);
                et.setSingleLine(true);
            }
            f.view = et;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            root.addView(et, lp);
        }

        // 长表单可滚动
        ScrollView sv = new ScrollView(ctx);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    // ============================================================
    // 回调
    // ============================================================

    public interface OnSubmit {
        void onSubmit(Map<String, String> values);
    }

    public interface OnNeutral {
        void onClick(Map<String, String> values);
    }

    /** 双按钮 */
    public void show(String posText, OnSubmit onSubmit, String negText, Runnable onNegative) {
        show(posText, onSubmit, null, null, negText, onNegative);
    }

    /** 三按钮 */
    public void show(String posText, OnSubmit onSubmit,
                     String neuText, OnNeutral onNeutral,
                     String negText, Runnable onNegative) {
        View view = buildView();
        Dialogs.Builder b = new Dialogs.Builder()
                .setView(view)
                .setCancelable(cancelable);
        if (title != null) b.setTitle(title);

        b.setPositiveButton(posText, (d, w) -> {
            if (onSubmit != null) onSubmit.onSubmit(collect());
        });
        if (neuText != null) {
            b.setNeutralButton(neuText, (d, w) -> {
                if (onNeutral != null) onNeutral.onClick(collect());
            });
        }
        b.setNegativeButton(negText == null ? "取消" : negText, (d, w) -> {
            if (onNegative != null) onNegative.run();
        });
        b.show();
    }

    /** 收集输入值（纯文本说明项不参与） */
    private Map<String, String> collect() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : fields) {
            if (f.isNote || f.key == null || f.view == null) continue;
            out.put(f.key, f.view.getText().toString());
        }
        return out;
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}