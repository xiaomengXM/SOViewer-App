package com.viewer.so.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 「打开方式」入口：接收 ACTION_VIEW / ACTION_SEND。
 *
 * 关键设计（与旧实现的重要差异）：
 *  1. 使用带透明背景的可见主题，而非 Theme.NoDisplay ——
 *     后者在 targetSdk 34 上要求 onCreate 内必须 finish，
 *     否则抛 "Activity did not call finish()" 异常。
 *  2. 定位为"中转"：拷贝文件完成后显式 startActivity(PreviewActivity)，
 *     再自行 finish，确保预览页在自身生命周期之外也能被拉起。
 *  3. 大文件警告等交互统一下沉到 PreviewActivity 处理，
 *     避免中转页存活期间被系统回收。
 */
public class OpenWithActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean launched = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.init(getApplicationContext());

        Uri uri = extractUri(getIntent());
        if (uri == null) {
            Dialogs.toast("未能获取文件");
            finish();
            return;
        }

        // 首次使用默认视为同意（完整协议确认在主页）
        if (!Prefs.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
            Prefs.putBoolean(Consts.PREF_KEY_EULA_AGREED, true);
        }

        PreviewService.start(this);
        PreviewEngine.ensureDefaultScripts(App.prefs());

        // 异步拷贝 + 直接拉起预览页
        App.io().submit(() -> copyThenLaunch(uri));
    }

    private void copyThenLaunch(Uri uri) {
        try {
            String name = FileImporter.queryDisplayName(this, uri);
            if (Utils.isEmpty(name)) name = "unnamed";

            // 拷贝到应用私有目录，规避 WebView 无法读取 content:// 的限制
            File dest = new File(Utils.tempReceivedDir(), Utils.sanitizeFileName(name));
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream os = new FileOutputStream(dest)) {
                if (is == null) throw new IllegalStateException("无法打开输入流");
                Utils.copyStream(is, os);
            }

            final String fileName = name;
            final File file = dest;

            main.post(() -> {
                launched = true;
                launchPreview(fileName, file);
            });
        } catch (Throwable t) {
            main.post(() -> Dialogs.toast("读取失败: " + t.getMessage()));
            main.post(this::safeFinish);
        }
    }

    /** 匹配脚本 → 处理大文件警告 → 启动预览页 */
    private void launchPreview(String fileName, File file) {
        List<JSONObject> matched = PreviewEngine.pickScriptsFor(fileName);

        if (matched.isEmpty()) {
            Dialogs.toast("未找到针对 ." + JsonUtils.getExtension(fileName) + " 的预览脚本");
            safeFinish();
            return;
        }

        if (matched.size() > 1) {
            String[] names = new String[matched.size()];
            for (int i = 0; i < matched.size(); i++) {
                names[i] = PreviewEngine.scriptDisplayName(matched.get(i));
            }
            new Dialogs.Builder()
                    .setTitle("选择预览方式")
                    .setItems(names, (d, which) -> {
                        launchWithScript(matched.get(which), fileName, file);
                    })
                    .setNegativeButton("取消", (d, w) -> safeFinish())
                    .setCancelable(false)
                    .show();
            return;
        }

        launchWithScript(matched.get(0), fileName, file);
    }

    private void launchWithScript(JSONObject script, String fileName, File file) {
        int warnMb = Prefs.getInt(Consts.PREF_KEY_LARGE_FILE_WARNING_MB,
                Consts.DEFAULT_LARGE_FILE_WARNING_MB, 1, 2048);
        long warnBytes = warnMb * 1024L * 1024L;

        if (file.length() > warnBytes) {
            new Dialogs.Builder()
                    .setTitle("文件过大警告")
                    .setMessage("文件大小超过 " + warnMb + "MB（"
                            + (file.length() / 1024 / 1024) + "MB）。\n"
                            + "继续预览可能导致卡顿或崩溃。\n是否继续？")
                    .setPositiveButton("继续", (d, w) -> {
                        MainActivity.pushRecent(fileName, file.getAbsolutePath());
                        PreviewActivity.launch(this, script, fileName, file);
                        safeFinish();
                    })
                    .setNegativeButton("取消", (d, w) -> safeFinish())
                    .setCancelable(false)
                    .show();
            return;
        }

        MainActivity.pushRecent(fileName, file.getAbsolutePath());
        PreviewActivity.launch(this, script, fileName, file);
        safeFinish();
    }

    private void safeFinish() {
        main.postDelayed(this::finish, 120);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 若因异常路径未完成中转，兜底 finish，避免 NoDisplay 类异常
        if (!launched) {
            // no-op：finish 已由各分支处理
        }
    }

    private Uri extractUri(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            return intent.getData();
        }
        if (Intent.ACTION_SEND.equals(action)) {
            try {
                return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } catch (Throwable t) {
                //noinspection deprecation
                return intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        return null;
    }
}