package com.viewer.so.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 「打开方式」入口：接收 ACTION_VIEW / ACTION_SEND，转交 MainActivity 处理。
 *
 * 无界面（Theme.NoDisplay），配合 excludeFromRecents，用户感知即"点开文件直接预览"。
 */
public class OpenWithActivity extends AppCompatActivity {

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

        // 首次使用需先同意协议
        if (!Prefs.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
            Prefs.putBoolean(Consts.PREF_KEY_EULA_AGREED, true);
        }

        PreviewService.start(this);
        PreviewEngine.ensureDefaultScripts(App.prefs());

        // 转交主逻辑处理（拷贝 → 匹配脚本 → 启动预览）
        MainActivity.handleExternalFile(this, uri);
        finish();
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
                // 兼容旧 API
                //noinspection deprecation
                return intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        return null;
    }
}