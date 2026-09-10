package com.viewer.so.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 完整设置页。覆盖原 Core.java UISettings（1443-2158 行）的全部配置项。
 *
 * 分组：
 *   1. 基础预览
 *   2. 脚本管理
 *   3. 网络与缓存
 *   4. AI / Agent
 *   5. 拓展功能
 *   6. 诊断
 *   7. 关于
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private SharedPreferences sp;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        App.init(requireContext().getApplicationContext());
        sp = App.prefs();

        // 使用与业务代码相同的偏好文件
        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName(Consts.PREFS_NAME);
        pm.setSharedPreferencesMode(android.content.Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        bindSummaries();
        bindActions();
    }

    // ============================================================
    // 摘要同步
    // ============================================================

    private void bindSummaries() {
        syncEditText(Consts.PREF_KEY_LARGE_FILE_WARNING_MB, "超过 %s MB 时提示");
        syncEditText(Consts.PREF_KEY_CACHE_MAX_MB, "上限 %s MB");
        syncEditText(Consts.PREF_KEY_SAVE_MAX_MB, "单次保存上限 %s MB");
        syncEditText(Consts.PREF_KEY_PRELOAD_MAX_MB, "预加载上限 %s MB");
        syncEditText(Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB, "代理响应上限 %s MB");
        syncEditText(Consts.PREF_KEY_SESSION_TTL_MINUTES, "会话有效期 %s 分钟");
        syncEditText(Consts.PREF_KEY_SERVER_PORT_HINT, "端口 %s（留空自动分配）");

        syncEditText(Consts.PREF_KEY_SCRIPT_API_URL, "接口地址：%s");
        syncEditText(Consts.PREF_KEY_SCRIPT_API_MODEL, "模型：%s");
        syncEditText(Consts.PREF_KEY_AGENT_MAX_ROUNDS, "最多 %s 轮");
        syncEditText(Consts.PREF_KEY_AGENT_MAX_TOKENS, "上限 %s tokens");
        syncEditText(Consts.PREF_KEY_AGENT_MAX_FILE_CHARS, "注入上限 %s 字符");
        syncEditText(Consts.PREF_KEY_AGENT_MEMORY_MAX_ITEMS, "记忆上限 %s 条");
        syncEditText(Consts.PREF_KEY_MCP_SDK_BASE_URL, "SDK：%s");
    }

    private void syncEditText(String key, String format) {
        EditTextPreference p = findPreference(key);
        if (p == null) return;
        String v = sp.getString(key, "");
        if (v == null || v.trim().isEmpty()) {
            v = p.getText() == null ? "" : p.getText();
        }
        p.setSummary(String.format(format, v));
        p.setOnPreferenceChangeListener(this);
    }

    // ============================================================
    // 动作绑定
    // ============================================================

    private void bindActions() {
        // 敏感值输入框改为密码样式
        maskEditText(Consts.PREF_KEY_SCRIPT_API_KEY);
        maskEditText(Consts.PREF_KEY_AGENT_SYSTEM_PROMPT, false);
        multilineEditText(Consts.PREF_KEY_MCP_SERVERS_JSON);
        multilineEditText(Consts.PREF_KEY_PROXY_ALLOWLIST);

        Preference clearCache = findPreference("action_clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(p -> {
                new Dialogs.Builder()
                        .setTitle("清理缓存")
                        .setMessage("将删除所有已缓存的远程资源，下次预览需重新下载。确定继续？")
                        .setPositiveButton("清理", (d, w) -> {
                            CacheManager.clearCache();
                            refreshCacheSummary();
                            Dialogs.toast("缓存已清理");
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        Preference clearSessions = findPreference("action_clear_sessions");
        if (clearSessions != null) {
            clearSessions.setOnPreferenceClickListener(p -> {
                PreviewEngine.clearSessions();
                Dialogs.toast("已清理所有预览会话");
                return true;
            });
        }

        Preference clearRecent = findPreference("action_clear_recent");
        if (clearRecent != null) {
            clearRecent.setOnPreferenceClickListener(p -> {
                Prefs.remove(Consts.PREF_KEY_RECENT_FILES);
                Dialogs.toast("最近文件记录已清空");
                return true;
            });
        }

        Preference resetScripts = findPreference("action_reset_scripts");
        if (resetScripts != null) {
            resetScripts.setOnPreferenceClickListener(p -> {
                new Dialogs.Builder()
                        .setTitle("重置内置脚本")
                        .setMessage("将把全部内置脚本恢复为默认版本，你自行添加的脚本会保留。确定继续？")
                        .setPositiveButton("重置", (d, w) -> {
                            sp.edit().putInt(Consts.PREF_KEY_SCRIPTS_VERSION, 0).apply();
                            PreviewEngine.ensureDefaultScripts(sp);
                            Dialogs.toast("内置脚本已重置");
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        Preference showScripts = findPreference("action_show_scripts");
        if (showScripts != null) {
            JSONArray arr = JsonUtils.parseArray(Prefs.getString(Consts.PREF_KEY_SCRIPTS, "[]"));
            showScripts.setSummary("当前共 " + arr.length() + " 个脚本，点击查看");
            showScripts.setOnPreferenceClickListener(p -> {
                showScriptList();
                return true;
            });
        }

        Preference eula = findPreference("action_eula");
        if (eula != null) {
            eula.setOnPreferenceClickListener(p -> {
                Prefs.putBoolean(Consts.PREF_KEY_EULA_AGREED, false);
                Dialogs.toast("已重置协议状态，下次启动将重新询问");
                return true;
            });
        }

        Preference github = findPreference("about_github");
        if (github != null) {
            github.setOnPreferenceClickListener(p -> {
                try {
                    startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/bilieebiliee1-design/SOPreviewer")));
                } catch (Throwable t) {
                    Dialogs.toast("无法打开浏览器");
                }
                return true;
            });
        }

        Preference license = findPreference("about_license");
        if (license != null) {
            license.setOnPreferenceClickListener(p -> {
                new Dialogs.Builder()
                        .setTitle("开源协议")
                        .setMessage("本项目基于 SOPreviewer 改造，遵循 GNU Affero General Public License v3.0。\n\n"
                                + "你可以在遵守 AGPL-3.0 的前提下自由使用、修改和分发本项目。")
                        .setPositiveButton("知道了", null)
                        .show();
                return true;
            });
        }
        Preference diag = findPreference("action_diagnostics");
        if (diag != null) {
            diag.setOnPreferenceClickListener(p -> {
                showDiagnostics();
                return true;
            });
        }

        refreshCacheSummary();
    }

    private void maskEditText(String key) {
        maskEditText(key, true);
    }

    private void maskEditText(String key, boolean password) {
        EditTextPreference p = findPreference(key);
        if (p == null) return;
        p.setOnBindEditTextListener(editText -> {
            if (password) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else {
                editText.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                editText.setMinLines(4);
            }
        });
        p.setOnPreferenceChangeListener(this);
    }

    private void multilineEditText(String key) {
        EditTextPreference p = findPreference(key);
        if (p == null) return;
        p.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setMinLines(4);
        });
        p.setOnPreferenceChangeListener(this);
    }

    // ============================================================
    // 变更回写
    // ============================================================

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key == null) return true;

        String value = newValue == null ? "" : String.valueOf(newValue);

        // 数值类：统一存字符串（与 Prefs.getInt 兼容）
        try {
            switch (key) {
                case Consts.PREF_KEY_LARGE_FILE_WARNING_MB:
                    preference.setSummary("超过 " + clamp(value, 50, 1, 2048) + " MB 时提示");
                    break;
                case Consts.PREF_KEY_CACHE_MAX_MB:
                    preference.setSummary("上限 " + clamp(value, 100, 1, 4096) + " MB");
                    break;
                case Consts.PREF_KEY_SAVE_MAX_MB:
                    preference.setSummary("单次保存上限 " + clamp(value, 80, 1, 512) + " MB");
                    break;
                case Consts.PREF_KEY_PRELOAD_MAX_MB:
                    preference.setSummary("预加载上限 " + clamp(value, 64, 1, 512) + " MB");
                    break;
                case Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB:
                    preference.setSummary("代理响应上限 " + clamp(value, 32, 1, 512) + " MB");
                    break;
                case Consts.PREF_KEY_SESSION_TTL_MINUTES:
                    preference.setSummary("会话有效期 " + clamp(value, 10, 1, 1440) + " 分钟");
                    break;
                case Consts.PREF_KEY_SERVER_PORT_HINT:
                    preference.setSummary(Utils.isEmpty(value)
                            ? "端口自动分配"
                            : "端口 " + clamp(value, 0, 0, 65535));
                    break;
                case Consts.PREF_KEY_SCRIPT_API_URL:
                    preference.setSummary("接口地址：" + (Utils.isEmpty(value) ? "未设置" : value));
                    break;
                case Consts.PREF_KEY_SCRIPT_API_MODEL:
                    preference.setSummary("模型：" + (Utils.isEmpty(value) ? "未设置" : value));
                    break;
                case Consts.PREF_KEY_AGENT_MAX_ROUNDS:
                    preference.setSummary("最多 " + clamp(value, 6, 1, 20) + " 轮");
                    break;
                case Consts.PREF_KEY_AGENT_MAX_TOKENS:
                    preference.setSummary("上限 " + clamp(value, 2048, 128, 32768) + " tokens");
                    break;
                case Consts.PREF_KEY_AGENT_MAX_FILE_CHARS:
                    preference.setSummary("注入上限 " + clamp(value, 12000, 0, 200000) + " 字符");
                    break;
                case Consts.PREF_KEY_AGENT_MEMORY_MAX_ITEMS:
                    preference.setSummary("记忆上限 " + clamp(value, 80, 0, 1000) + " 条");
                    break;
                case Consts.PREF_KEY_MCP_SDK_BASE_URL:
                    preference.setSummary("SDK：" + (Utils.isEmpty(value) ? "默认" : value));
                    break;
                case Consts.PREF_KEY_SCRIPT_TRUSTED_MODE:
                    Dialogs.toast(value.equals("true")
                            ? "已开启可信模式：脚本将获得 API Key 与保存权限"
                            : "已关闭可信模式");
                    break;
                case Consts.PREF_KEY_SCRIPT_API_KEY:
                    Dialogs.toast("API Key 已保存");
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {}

        return true;
    }

    private static String clamp(String raw, int def, int min, int max) {
        try {
            int v = Integer.parseInt(raw.trim());
            return String.valueOf(Math.max(min, Math.min(max, v)));
        } catch (Throwable t) {
            return String.valueOf(def);
        }
    }

    // ============================================================
    // 动态对话框
    // ============================================================

    private void showScriptList() {
        try {
            JSONArray arr = JsonUtils.parseArray(Prefs.getString(Consts.PREF_KEY_SCRIPTS, "[]"));
            if (arr.length() == 0) {
                Dialogs.toast("暂无脚本");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                sb.append(i + 1).append(". ")
                        .append(JsonUtils.optString(o, "name", "?"))
                        .append("  [").append(JsonUtils.optString(o, "ext", "")).append("]");
                if (JsonUtils.optBoolean(o, "trusted", false)) sb.append("  ★可信");
                if (!JsonUtils.optBoolean(o, "enabled", true)) sb.append("  ⨯已停用");
                sb.append('\n');
            }
            new Dialogs.Builder()
                    .setTitle("预览脚本")
                    .setMessage(sb.toString())
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            Dialogs.toast("读取脚本失败");
        }
    }

    private void showDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("本地服务：");
        sb.append(HttpServer.currentPort > 0
                ? "运行中，端口 " + HttpServer.currentPort
                : "未启动");
        sb.append('\n');

        sb.append("活跃会话：").append(PreviewEngine.sessionCount()).append(" 个\n");
        sb.append("缓存大小：").append(Utils.humanSize(Utils.dirSize(Utils.webCacheDir()))).append('\n');
        sb.append("临时文件：").append(Utils.countFiles(Utils.tempReceivedDir())).append(" 个\n");
        sb.append("导出文件：").append(Utils.countFiles(Utils.exportsDir())).append(" 个\n\n");

        sb.append("脚本版本：")
                .append(sp.getInt(Consts.PREF_KEY_SCRIPTS_VERSION, 0))
                .append(" / 期望 ").append(Consts.CURRENT_SCRIPTS_VERSION)
                .append('\n');

        boolean apiOk = !Utils.isEmpty(Prefs.getString(Consts.PREF_KEY_SCRIPT_API_URL, ""));
        sb.append("AI 接口：").append(apiOk ? "已配置" : "未配置").append('\n');
        sb.append("可信模式：")
                .append(Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false) ? "开启" : "关闭")
                .append('\n');
        sb.append("代理：")
                .append(Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false) ? "开启" : "关闭");

        new Dialogs.Builder()
                .setTitle("诊断信息")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void refreshCacheSummary() {
        Preference p = findPreference("action_clear_cache");
        if (p == null) return;
        long size = Utils.dirSize(Utils.webCacheDir());
        p.setSummary("当前占用 " + Utils.humanSize(size));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCacheSummary();
    }
}