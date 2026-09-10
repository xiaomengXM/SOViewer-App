package com.viewer.so.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 主页：最近文件列表 + 打开文件 + 设置入口。
 */
public class MainActivity extends AppCompatActivity {

    private RecentAdapter adapter;
    private View emptyView;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                onFilePicked(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.init(getApplicationContext());
        setContentView(R.layout.activity_main);

        // 使用协议首次确认
        if (!Prefs.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
            showEula();
            return;
        }
        initUi();
    }

    private void initUi() {
        setContentView(R.layout.activity_main);

        emptyView = findViewById(R.id.empty_view);
        RecyclerView list = findViewById(R.id.recent_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecentAdapter(this::openRecent);
        list.setAdapter(adapter);

        findViewById(R.id.btn_open_file).setOnClickListener(v ->
                filePicker.launch(new String[]{"*/*"}));

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        PreviewService.start(this);
        reloadRecent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) reloadRecent();
    }

    // ============================================================
    // 最近文件
    // ============================================================

    private void reloadRecent() {
        List<RecentItem> items = loadRecent();
        adapter.submit(items);
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private List<RecentItem> loadRecent() {
        List<RecentItem> out = new ArrayList<>();
        try {
            JSONArray arr = JsonUtils.parseArray(Prefs.getString(Consts.PREF_KEY_RECENT_FILES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = JsonUtils.optString(o, "name", "");
                String path = JsonUtils.optString(o, "path", "");
                long time = JsonUtils.optLong(o, "time", 0);
                if (Utils.isEmpty(name) || Utils.isEmpty(path)) continue;
                out.add(new RecentItem(name, path, time));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** 记录最近打开（最多保留 30 条，去重） */
    public static void pushRecent(String name, String path) {
        try {
            JSONArray old = JsonUtils.parseArray(Prefs.getString(Consts.PREF_KEY_RECENT_FILES, "[]"));
            JSONArray fresh = new JSONArray();
            JSONObject head = new JSONObject();
            head.put("name", name);
            head.put("path", path);
            head.put("time", System.currentTimeMillis());
            fresh.put(head);

            int count = 1;
            for (int i = 0; i < old.length() && count < 30; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (path.equals(JsonUtils.optString(o, "path", ""))) continue; // 去重
                fresh.put(o);
                count++;
            }
            Prefs.putString(Consts.PREF_KEY_RECENT_FILES, fresh.toString());
        } catch (Throwable ignored) {}
    }

    private void openRecent(RecentItem item) {
        File f = new File(item.path);
        if (!f.exists()) {
            Dialogs.toast("文件已不存在：" + item.name);
            return;
        }
        startPreview(item.name, f);
    }

    // ============================================================
    // 打开外部文件
    // ============================================================

    private void onFilePicked(Uri uri) {
        Dialogs.toast("正在读取文件…");
        App.io().submit(() -> {
            try {
                String name = FileImporter.queryDisplayName(this, uri);
                if (Utils.isEmpty(name)) name = "unnamed";

                // 拷入应用私有目录：规避 WebView 对 content:// 的读取限制
                File dest = new File(Utils.tempReceivedDir(), Utils.sanitizeFileName(name));
                Utils.ensureParent(dest);
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream os = new FileOutputStream(dest)) {
                    if (is == null) throw new IllegalStateException("无法打开输入流");
                    Utils.copyStream(is, os);
                }
                final String fName = name;
                final File fDest = dest;
                main.post(() -> startPreview(fName, fDest));
            } catch (Throwable t) {
                Dialogs.toast("读取失败: " + t.getMessage());
            }
        });
    }

    // ============================================================
    // 预览启动
    // ============================================================

    private void startPreview(String fileName, File file) {
        openPreviewActivity(this, fileName, file);
    }

    private static void openPreviewActivity(AppCompatActivity ctx, String fileName, File file) {
        // 脚本匹配
        List<JSONObject> matched = PreviewEngine.pickScriptsFor(fileName);

        if (matched.isEmpty()) {
            Dialogs.toast("未找到针对 ." + JsonUtils.getExtension(fileName) + " 的预览脚本");
            return;
        }

        if (matched.size() == 1) {
            launchWithScript(ctx, matched.get(0), fileName, file);
            return;
        }

        // 多个脚本匹配 → 让用户选择
        String[] names = new String[matched.size()];
        for (int i = 0; i < matched.size(); i++) {
            names[i] = PreviewEngine.scriptDisplayName(matched.get(i));
        }
        new Dialogs.Builder()
                .setTitle("选择预览方式")
                .setItems(names, (d, which) -> launchWithScript(ctx, matched.get(which), fileName, file))
                .show();
    }

    private static void launchWithScript(AppCompatActivity ctx, JSONObject script, String fileName, File file) {
        // 大文件警告
        int warnMb = Prefs.getInt(Consts.PREF_KEY_LARGE_FILE_WARNING_MB,
                Consts.DEFAULT_LARGE_FILE_WARNING_MB, 1, 2048);
        long warnBytes = warnMb * 1024L * 1024L;
        if (file.length() > warnBytes) {
            new Dialogs.Builder()
                    .setTitle("文件过大警告")
                    .setMessage("文件大小超过 " + warnMb + "MB（"
                            + (file.length() / 1024 / 1024) + "MB）。\n继续预览可能导致卡顿或崩溃。\n是否继续？")
                    .setPositiveButton("继续", (d, w) -> {
                        pushRecent(fileName, file.getAbsolutePath());
                        PreviewActivity.launch(ctx, script, fileName, file);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        pushRecent(fileName, file.getAbsolutePath());
        PreviewActivity.launch(ctx, script, fileName, file);
    }

    // ============================================================
    // EULA
    // ============================================================

    private void showEula() {
        String text = "本应用为本地文件预览工具，所有解析均在设备本地完成。\n\n"
                + "· 预览脚本为本地 JavaScript，默认无网络与文件写入权限。\n"
                + "· 开启 AI / MCP / 代理等联网能力将发送数据至你配置的第三方服务。\n"
                + "· 请勿使用本工具处理来源不明的敏感文件。\n\n"
                + "本项目基于 SOPreviewer（AGPL-3.0）改造成独立应用。";

        new Dialogs.Builder()
                .setTitle("使用协议")
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton("同意并继续", (d, w) -> {
                    Prefs.putBoolean(Consts.PREF_KEY_EULA_AGREED, true);
                    initUi();
                })
                .setNegativeButton("退出", (d, w) -> finish())
                .show();
    }

    // ============================================================
    // 数据模型
    // ============================================================

    public static final class RecentItem {
        public final String name;
        public final String path;
        public final long time;

        public RecentItem(String name, String path, long time) {
            this.name = name;
            this.path = path;
            this.time = time;
        }
    }
}