package com.viewer.so.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;

/**
 * 预览容器：承载本地 HTTP 预览页的 WebView。
 *
 * 原插件通过 context.openBuiltinBrowser(url, showTopBar) 打开 MT 内置浏览器，
 * 独立 App 中由本 Activity 提供等价能力。
 */
public class PreviewActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_TOP_BAR = "top_bar";
    private static final String EXTRA_SCRIPT = "script";
    private static final String EXTRA_FILE_NAME = "file_name";
    private static final String EXTRA_FILE_PATH = "file_path";

    private WebView webView;
    private ProgressBar progress;
    private TextView titleView;

    /**
     * 由脚本直接启动（MainActivity 调用）：先生成预览页，再切到本 Activity。
     */
    public static void launch(AppCompatActivity ctx, JSONObject script, String fileName, File file) {
        PreviewEngine.startPreview(script, fileName, file, (url, showTopBar) -> {
            Intent i = new Intent(ctx, PreviewActivity.class);
            i.putExtra(EXTRA_URL, url);
            i.putExtra(EXTRA_TITLE, fileName);
            i.putExtra(EXTRA_TOP_BAR, showTopBar);
            ctx.startActivity(i);
        });
    }

    /** 直接用已有 URL 打开（供外部复用） */
    public static void launchUrl(AppCompatActivity ctx, String url, String title, boolean topBar) {
        Intent i = new Intent(ctx, PreviewActivity.class);
        i.putExtra(EXTRA_URL, url);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_TOP_BAR, topBar);
        ctx.startActivity(i);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.init(getApplicationContext());
        setContentView(R.layout.activity_preview);
        PreviewService.start(this);

        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        titleView = findViewById(R.id.preview_title);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        boolean topBar = getIntent().getBooleanExtra(EXTRA_TOP_BAR, true);

        if (Utils.isEmpty(url)) {
            Dialogs.toast("预览地址无效");
            finish();
            return;
        }

        // 顶栏开关（对应原 PREF_KEY_BROWSER_TOP_BAR_ENABLED）
        View topBarView = findViewById(R.id.top_bar);
        topBarView.setVisibility(topBar ? View.VISIBLE : View.GONE);
        if (title != null) titleView.setText(title);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_reload).setOnClickListener(v -> webView.reload());

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl() == null ? "" : request.getUrl().toString();
                // 仅允许本地回环，其余交给系统浏览器，防止预览页跳转外部内容
                if (u.startsWith("http://127.0.0.1") || u.startsWith("http://localhost")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Throwable ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if (Prefs.getBoolean(Consts.PREF_KEY_DEBUG_CONSOLE, false)) {
                    android.util.Log.d("SOViewer-Web", cm.message()
                            + " @" + cm.sourceId() + ":" + cm.lineNumber());
                }
                return true;
            }
        });

        webView.loadUrl(url);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            }
        } catch (Throwable ignored) {}
        webView = null;
        super.onDestroy();
    }
}