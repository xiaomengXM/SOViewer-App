package com.viewer.so.app;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 完整设置页。对应原 Core.java buildSettings（1309-1442 行）+ UISettings 全部对话框。
 *
 * 结构（与原版一一对应）：
 *   「我现在要做什么」  4 个新手引导 + 恢复新手默认 + 显示高级设置
 *   「状态总览」        当前配置 / 快速添加脚本 / API 配置
 *   「脚本」            动态脚本列表，每项可点开管理菜单
 *   「高级能力」        兼容/深色/调试/会话清理/可信/API/MCP/Agent ×8/代理/策略/外部联动/顶栏/辅助应用/落盘/目录
 *   「维护」            缓存管理 / 备份导入 / 清理缓存 / 重置内置脚本
 *   「文档」            总览/手册/架构/API/安全/声明/交流群
 *   「关于」            项目主页 / 开源协议 / 使用协议
 *
 * 关键机制：原版用 item.getPreferenceScreen().recreate() 实现"改完立即刷新"，
 * 这里用 recreateScreen() 封装（重建 PreferenceFragment）。
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private SharedPreferences sp;

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        App.init(requireContext().getApplicationContext());
        sp = App.prefs();

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName(Consts.PREFS_NAME);
        pm.setSharedPreferencesMode(android.content.Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        // 未同意协议时隐藏全部设置（原版 builder.onCreated 里的守卫）
        if (!sp.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
            showEulaGate();
            return;
        }

        bindSummaries();
        bindActions();
        bindScriptList();
        applyAdvancedVisibility();
    }

    /** EULA 未同意时的栅栏页 */
    private void showEulaGate() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        Preference gate = new Preference(requireContext());
        gate.setTitle("阅读并同意使用协议");
        gate.setSummary("点击查看协议，确认后才会显示完整设置");
        gate.setOnPreferenceClickListener(p -> {
            showEulaDialog(this::recreateScreen, null);
            return true;
        });
        screen.addPreference(gate);
    }

    /** 重建当前设置页（等价原版 item.getPreferenceScreen().recreate()） */
    private void recreateScreen() {
        try {
            setPreferencesFromResource(R.xml.preferences, null);
            if (!sp.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
                showEulaGate();
                return;
            }
            bindSummaries();
            bindActions();
            bindScriptList();
            applyAdvancedVisibility();
        } catch (Throwable t) {
            android.util.Log.e("SOViewer", "recreateScreen failed", t);
        }
    }

    // ============================================================
    // 摘要同步
    // ============================================================

    private void bindSummaries() {
        syncSummary("action_overview", buildSettingsOverview());
        syncSummary("action_api_config", buildApiSummary());
        syncSummary("action_mcp_config", buildMcpAgentSummary());
        syncSummary("action_advanced_policy", buildAdvancedPolicySummary());
        syncSummary("action_save_dir", "当前: " + Utils.getPreferredDownloadDirectoryDisplay());
        syncSummary("action_cache", "当前占用 " + Utils.humanSize(Utils.dirSize(Utils.webCacheDir())));
        syncSummary("action_script_backup", "导出当前脚本 JSON，或从备份覆盖恢复");
        syncSummary("action_ext_app", Utils.isPackageInstalled("com.viewer.so.app")
                ? "已安装 · 可重新安装或修复关联"
                : "未安装 · 点击安装以启用外部联动");
    }

    private void syncSummary(String key, CharSequence summary) {
        Preference p = findPreference(key);
        if (p != null) p.setSummary(summary);
    }

    /** 状态总览：脚本数 / 能力开关 / 策略 / 导出目录 */
    private String buildSettingsOverview() {
        JSONArray arr = ScriptRepo.all(sp);
        int[] c = ScriptRepo.count(arr);
        return "脚本 " + arr.length() + " 个（内置 " + c[0] + " · 自定义 " + c[1] + " · 可信 " + c[2] + "）\n"
                + (sp.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false) ? "可信增强已开启" : "最小权限模式")
                + " · " + (sp.getBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false) ? "代理开启" : "代理关闭");
    }

    private String buildApiSummary() {
        boolean enabled = sp.getBoolean(Consts.PREF_KEY_SCRIPT_API_ENABLED, false);
        String url = sp.getString(Consts.PREF_KEY_SCRIPT_API_URL, "").trim();
        String model = sp.getString(Consts.PREF_KEY_SCRIPT_API_MODEL, "").trim();
        String key = sp.getString(Consts.PREF_KEY_SCRIPT_API_KEY, "").trim();
        if (!enabled) return "未开启";
        return "端点 " + (url.isEmpty() ? "未填" : "已填")
                + " · 模型 " + (model.isEmpty() ? "未填" : model)
                + " · 密钥 " + (key.isEmpty() ? "未填" : "已填");
    }

    private String buildMcpAgentSummary() {
        boolean enabled = sp.getBoolean(Consts.PREF_KEY_MCP_AGENT_ENABLED, false);
        int servers = 0;
        try {
            JSONArray a = new JSONArray(sp.getString(Consts.PREF_KEY_MCP_SERVERS_JSON, "[]"));
            servers = a.length();
        } catch (Throwable ignored) {}
        return (enabled ? "已开启" : "未开启") + " · MCP 服务 " + servers + " 个";
    }

    private String buildAdvancedPolicySummary() {
        return "大文件 " + Utils.getIntPref(sp, Consts.PREF_KEY_LARGE_FILE_WARNING_MB, 50, 1, 2048) + "MB"
                + " · 缓存 " + Utils.getIntPref(sp, Consts.PREF_KEY_CACHE_MAX_MB, 100, 0, 4096) + "MB"
                + " · 保存/预载/代理 "
                + Utils.getIntPref(sp, Consts.PREF_KEY_SAVE_MAX_MB, 80, 1, 512) + "/"
                + Utils.getIntPref(sp, Consts.PREF_KEY_PRELOAD_MAX_MB, 64, 1, 512) + "/"
                + Utils.getIntPref(sp, Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB, 32, 1, 512) + "MB";
    }

    // ============================================================
    // 高级模式显隐（对应原版「显示高级设置」开关）
    // ============================================================

    private void applyAdvancedVisibility() {
        boolean advanced = sp.getBoolean(Consts.PREF_KEY_SHOW_ADVANCED_SETTINGS, false);

        // 高级模式下才显示的分组
        setCategoryVisible("cat_scripts", advanced);
        setCategoryVisible("cat_advanced", advanced);
        setCategoryVisible("cat_maintain", advanced);

        // 高级模式下才显示的单向开关（新手无需接触）
        setPrefVisible("script_trusted_mode", advanced);
        setPrefVisible("script_save_enabled", advanced);
        setPrefVisible("script_proxy_enabled", advanced);
        setPrefVisible("mcp_agent_enabled", advanced);
        setPrefVisible("agent_planner_enabled", advanced);
        setPrefVisible("agent_file_context_enabled", advanced);
        setPrefVisible("agent_vision_enabled", advanced);
        setPrefVisible("agent_memory_enabled", advanced);
        setPrefVisible("agent_history_enabled", advanced);
        setPrefVisible("agent_auto_memory_enabled", advanced);
        setPrefVisible("debug_console", advanced);
        setPrefVisible("script_api_enabled", advanced);

        // 条件显示：可信增强未开时，隐藏单脚本相关的说明性项
        setPrefVisible("action_save_dir",
                sp.getBoolean(Consts.PREF_KEY_SCRIPT_SAVE_ENABLED, true));
        setPrefVisible("action_ext_app",
                sp.getBoolean("ext_open_enabled", false));
    }

    private void setCategoryVisible(String key, boolean visible) {
        PreferenceCategory c = findPreference(key);
        if (c != null) c.setVisible(visible);
    }

    private void setPrefVisible(String key, boolean visible) {
        Preference p = findPreference(key);
        if (p != null) p.setVisible(visible);
    }

    // ============================================================
    // 动作绑定（第一组：引导 / 状态 / 维护 / 文档 / 关于）
    // ============================================================

    private void bindActions() {
        // ---------- 我现在要做什么 ----------
        onClick("guide_preview", () -> showPreviewBeginnerGuide());
        onClick("guide_so", this::showSoAnalysisBeginnerGuide);
        onClick("guide_aimcp", this::showAiMcpBeginnerGuide);
        onClick("guide_panic", this::showPanicHelp);
        onClick("action_reset_beginner", this::confirmResetBeginnerDefaults);
        onClick("action_add_script", () -> showScriptDialog(null, -1));
        onClick("action_overview", this::showEnvCheck);
        onClick("action_api_config", this::showApiDialog);

        // ---------- 高级能力：子对话框入口 ----------
        onClick("action_mcp_config", this::showMcpAgentDialog);
        onClick("action_advanced_policy", this::showAdvancedPolicyDialog);
        onClick("action_ext_app", this::showExtDialog);
        onClick("action_save_dir", this::showDirDialog);

        // ---------- 维护 ----------
        onClick("action_cache", this::showCacheMgmt);
        onClick("action_script_backup", this::showScriptBackupDialog);
        onClick("action_clear_cache", () -> new Dialogs.Builder()
                .setTitle("清理缓存")
                .setMessage("确定要清理所有缓存文件吗？下次预览时将重新下载所需资源。")
                .setPositiveButton("确定", (d, w) -> clearCache())
                .setNegativeButton("取消", null)
                .show());
        onClick("action_reset_scripts", () -> new Dialogs.Builder()
                .setTitle("重置内置脚本")
                .setMessage("确定要重置所有内置脚本到初始状态吗？你的自定义脚本不会被修改。")
                .setPositiveButton("确定", (d, w) -> {
                    sp.edit().putInt(Consts.PREF_KEY_SCRIPTS_VERSION, 0).apply();
                    PreviewEngine.ensureDefaultScripts(sp);
                    Dialogs.toast("内置脚本已重置");
                    recreateScreen();
                })
                .setNegativeButton("取消", null)
                .show());

        // ---------- 文档 ----------
        onClick("doc_quick", () -> showDocDialog("功能总览与上手路径",
                "1. 只想预览文件：主页点「打开文件」，或用文件管理器「打开方式」。\n\n"
                        + "2. 想分析 SO：打开 .so 文件，内置 SO 高级预览会自动匹配。\n\n"
                        + "3. 想看文档数据：.md / .pdf / .json / .xml / .csv / .db / 图片 均按内置脚本渲染。\n\n"
                        + "4. 想用 AI / MCP：先在「AI 与 Agent」填 API 配置，再开启 MCP Agent。\n\n"
                        + "5. 想从其他文件管理器调用：开启「外部应用联动」。\n\n"
                        + "常用开关怎么选：\n"
                        + "· 兼容模式：旧 WebView 白屏时开启，正常别开（会变慢）\n"
                        + "· 深色预览主题：夜间阅读开启\n"
                        + "· 调试控制台：脚本报错时开启\n"
                        + "· 可信脚本增强：需要 API Key 或落盘时才开，还要给具体脚本单独授权\n"
                        + "· 网络请求代理：脚本需要访问远程 API 时开启，建议同时配置白名单"));
        onClick("doc_manual", () -> showDocDialog("完整使用说明",
                "【基础使用】\n"
                        + "· 打开文件：主页「打开文件」，或任意文件管理器「打开方式 / 分享」\n"
                        + "· 进入设置：主页右上角齿轮图标\n"
                        + "· 添加脚本：设置页「快速添加脚本」，支持任意扩展名\n\n"
                        + "【核心功能】\n"
                        + "本应用是基于自定义脚本的多功能文件预览工具，支持 SO、Markdown、SQL、PDF 等格式，"
                        + "并允许通过 JavaScript 脚本扩展更多预览能力。\n\n"
                        + "【安全与增强】\n"
                        + "1. 默认仅开放基础预览能力。\n"
                        + "2. 脚本需访问网络时，开启「网络请求代理」。\n"
                        + "3. 需本地落盘或敏感 API 密钥时，必须主动开启「可信脚本增强」并单独授权脚本。\n"
                        + "4. 仅对能审计且完全信任的脚本开启增强能力。\n\n"
                        + "【排错指引】\n"
                        + "1. 没有匹配脚本：检查文件后缀，或在设置页添加脚本\n"
                        + "2. 预览白屏：开启调试控制台；旧 WebView 再开兼容模式；必要时重置内置脚本\n"
                        + "3. AI 不能请求：检查 API 地址、模型、密钥、代理开关和白名单\n"
                        + "4. 保存失败：检查文件落盘开关、可信授权、保存目录是否可写"));
        onClick("doc_arch", () -> showDocDialog("系统架构与演进路线",
                "【产品边界】\n"
                        + "本应用是独立文件预览工具，源自 SOPreviewer（MT 管理器插件）的完整移植，"
                        + "不再依赖任何宿主应用。\n\n"
                        + "【能力层级】\n"
                        + "第 1 层：本地静态预览（HTML / 文本 / JSON / XML / CSV / 图片 / Markdown / PDF / SQLite）\n"
                        + "第 2 层：深度解析（SO 高级预览：ELF 结构、符号、字符串、反汇编、风险特征）\n"
                        + "第 3 层：联网增强（AI 接口 / MCP Agent / 网络代理）\n\n"
                        + "【技术架构】\n"
                        + "· 本地 HTTP Server（127.0.0.1）承载预览会话，规避 WebView 的 file:// 限制\n"
                        + "· 预览脚本为 JavaScript，由容器模板加载执行\n"
                        + "· 脚本通过 window.SOViewer* 全局对象访问受限能力\n\n"
                        + "【演进方向】\n"
                        + "脚本市场、更多格式内置支持、离线 AI 本地推理"));
        onClick("doc_api", () -> showDocDialog("API 与代理调用指南",
                "【全局对象】\n"
                        + "window.SO_API_URL       —— API 端点（开启「启用 AI 接口」后注入）\n"
                        + "window.SO_MODEL_ID      —— 模型 ID\n"
                        + "window.SO_API_KEY       —— API 密钥（仅可信脚本可读取）\n"
                        + "window.SO_PROXY_BASE   —— 代理基址（开启代理后为 /proxy/）\n\n"
                        + "window.SOViewerSession  —— 当前会话信息\n"
                        + "  · token           会话令牌\n"
                        + "  · saveEndpoint    保存接口地址\n"
                        + "  · preloadEndpoint 预加载接口地址\n\n"
                        + "window.SOViewerLimits   —— 各项资源上限\n\n"
                        + "【代理调用】\n"
                        + "开启「网络请求代理」后，脚本可通过 /proxy/&lt;目标URL&gt; 访问公网，"
                        + "规避 WebView 跨域限制。建议配置白名单限制可访问域名。\n\n"
                        + "【保存文件】\n"
                        + "需同时满足：可信增强开启 + 当前脚本被单独授权 + 文件落盘开启"));
        onClick("doc_security", () -> showDocDialog("安全模式说明",
                "【脚本执行】\n"
                        + "预览脚本在 WebView 沙箱内运行，默认无网络与文件写入权限。\n"
                        + "脚本代码会注入 HTML 模板，因此仅应加载你信任的脚本。\n\n"
                        + "【HTML 隔离】\n"
                        + "HTML 预览使用 iframe sandbox 属性隔离，限制页面脚本能力。\n\n"
                        + "【代理访问】\n"
                        + "网络请求代理默认关闭。开启后所有脚本均可通过本地代理访问公网，"
                        + "强烈建议配置域名白名单。\n\n"
                        + "【缓存策略】\n"
                        + "第三方资源缓存在应用私有目录，可随时清理。缓存内容不加密，请勿存放敏感数据。\n\n"
                        + "【密钥安全】\n"
                        + "API 密钥默认不对脚本注入，仅在「可信增强 + 单脚本授权」双开关下才可读取。"));
        onClick("doc_legal", this::showLegalDialog);
        onClick("doc_qq", () -> {
            try {
                startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1079912856&card_type=group&source=qrcode")));
            } catch (Throwable t) {
                Dialogs.toast("无法打开 QQ，请手动添加群: 1079912856");
            }
        });

        // ---------- 关于 ----------
        onClick("about_github", () -> {
            try {
                startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/xiaomengXM/SOViewer-App")));
            } catch (Throwable t) {
                Dialogs.toast("无法打开浏览器");
            }
        });
        onClick("about_license", this::showOpenSourceDialog);
        onClick("action_eula", this::showEulaViewer);
    }

    private void onClick(String key, Runnable action) {
        Preference p = findPreference(key);
        if (p == null) return;
        p.setOnPreferenceClickListener(x -> {
            try {
                action.run();
            } catch (Throwable t) {
                android.util.Log.e("SOViewer", "action failed: " + key, t);
                Dialogs.toast("操作失败: " + t.getMessage());
            }
            return true;
        });
    }

    // ============================================================
    // 动态脚本列表（对应原版「脚本」分组）
    // ============================================================

    private void bindScriptList() {
        PreferenceCategory cat = findPreference("cat_scripts");
        if (cat == null) return;

        // 清空后重建（保留分组本身）
        cat.removeAll();

        JSONArray arr = ScriptRepo.all(sp);
        if (arr.length() == 0) {
            Preference empty = new Preference(requireContext());
            empty.setTitle("暂无脚本");
            empty.setSummary("点击上方「快速添加脚本」创建第一个预览脚本");
            empty.setEnabled(false);
            cat.addPreference(empty);
            return;
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            boolean builtin = JsonUtils.optBoolean(s, "builtin", false);
            final int idx = i;

            Preference item = new Preference(requireContext());
            item.setKey("script_item_" + i);
            item.setTitle((builtin ? "内置 · " : "自定义 · ")
                    + JsonUtils.optString(s, "name", "未命名脚本"));
            item.setSummary(ScriptRepo.summaryOf(builtin,
                    JsonUtils.optString(s, "ext", ""), sp, s));
            item.setOnPreferenceClickListener(p -> {
                showScriptManageMenu(idx);
                return true;
            });
            cat.addPreference(item);
        }
    }

    /** 脚本管理菜单（对应原版 showEditDialog） */
    private void showScriptManageMenu(int idx) {
        JSONArray arr = ScriptRepo.all(sp);
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject target = arr.optJSONObject(idx);
        if (target == null) return;

        boolean globalTrusted = sp.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false);
        boolean trusted = PreviewEngine.isScriptTrusted(target);

        String[] actions = globalTrusted
                ? new String[]{"编辑", "启用/禁用", "上移", "下移", "复制为新脚本",
                               trusted ? "关闭可信授权" : "开启可信授权", "删除"}
                : new String[]{"编辑", "启用/禁用", "上移", "下移", "复制为新脚本", "删除"};

        new Dialogs.Builder()
                .setTitle("管理脚本")
                .setItems(actions, (d, w) -> {
                    String pick = actions[w];
                    switch (pick) {
                        case "编辑":
                            showScriptDialog(target, idx);
                            break;
                        case "启用/禁用": {
                            boolean enabled = ScriptRepo.toggleEnabled(idx);
                            Dialogs.toast(enabled ? "脚本已启用" : "脚本已禁用");
                            recreateScreen();
                            break;
                        }
                        case "上移": {
                            if (!ScriptRepo.moveUp(idx)) Dialogs.toast("已经在最前面");
                            else { Dialogs.toast("已上移"); recreateScreen(); }
                            break;
                        }
                        case "下移": {
                            if (!ScriptRepo.moveDown(idx)) Dialogs.toast("已经在最后面");
                            else { Dialogs.toast("已下移"); recreateScreen(); }
                            break;
                        }
                        case "复制为新脚本":
                            ScriptRepo.duplicate(idx);
                            Dialogs.toast("已复制为新脚本");
                            recreateScreen();
                            break;
                        case "开启可信授权":
                        case "关闭可信授权": {
                            boolean now = ScriptRepo.toggleTrusted(idx);
                            Dialogs.toast(now ? "已允许该脚本使用可信增强能力"
                                              : "已关闭该脚本的可信增强能力");
                            recreateScreen();
                            break;
                        }
                        case "删除":
                            confirmDeleteScript(idx);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void confirmDeleteScript(int idx) {
        JSONArray arr = ScriptRepo.all(sp);
        if (idx < 0 || idx >= arr.length()) return;
        String name = JsonUtils.optString(arr.optJSONObject(idx), "name", "");
        new Dialogs.Builder()
                .setTitle("确认删除")
                .setMessage("确定要删除脚本 \"" + name + "\" 吗？")
                .setPositiveButton("删除", (d, w) -> {
                    ScriptRepo.removeAt(idx);
                    Dialogs.toast("已删除");
                    recreateScreen();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ============================================================
    // 脚本编辑表单（对应原版 showScriptDialog）
    // ============================================================

    private static final String DEFAULT_SCRIPT_CODE =
            "function render(fileName, fileExt, content) {\n"
                    + "    const div = document.createElement('div');\n"
                    + "    const text = new TextDecoder().decode(content);\n"
                    + "    div.textContent = text;\n"
                    + "    return div;\n"
                    + "}";

    private void showScriptDialog(JSONObject obj, int idx) {
        boolean isNew = obj == null;
        String n = isNew ? "" : JsonUtils.optString(obj, "name", "");
        String e = isNew ? "" : JsonUtils.optString(obj, "ext", "");
        String c = isNew ? DEFAULT_SCRIPT_CODE : JsonUtils.optString(obj, "code", "");
        boolean globalTrusted = sp.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false);
        boolean trusted = !isNew && PreviewEngine.isScriptTrusted(obj);

        FormDialog fd = new FormDialog(requireContext())
                .title(isNew ? "添加脚本" : "编辑脚本")
                .desc("在这里配置脚本名称、匹配扩展名和脚本代码。高风险能力的授权状态会单独显示。");

        if (!globalTrusted) {
            fd.note("当前限制",
                    "当前未开启全局可信增强能力，因此这里不会显示单脚本授权开关。"
                            + "请先到设置页开启「可信脚本增强」。");
        } else {
            fd.note("可信增强说明",
                    "当前已开启全局可信增强能力。是否允许当前脚本获得 API 密钥与本地落盘能力，"
                            + "请使用「可信」按钮明确操作。代理只受全局网络请求代理开关控制。");
            fd.note("当前状态", trusted ? "已授权可信增强" : "未授权可信增强");
        }

        fd.input("n", "名称", n, "例如：我的自定义预览")
          .input("e", "扩展名（不含点，逗号分隔）", e, "例如：log,conf")
          .multiline("c", "脚本 (JavaScript)", c, "function render(fileName, fileExt, content) { ... }");

        if (globalTrusted) {
            fd.show("保存", values -> saveScript(values, obj, idx),
                    trusted ? "关闭可信" : "开启可信",
                    values -> {
                        // 先保存编辑内容，再切换可信位
                        saveScriptQuiet(values, obj, idx);
                        ScriptRepo.toggleTrusted(idx >= 0 ? idx : ScriptRepo.all(sp).length() - 1);
                        Dialogs.toast(trusted ? "已关闭当前脚本可信增强" : "已开启当前脚本可信增强");
                        recreateScreen();
                    },
                    "取消", null);
        } else {
            fd.show("保存", values -> saveScript(values, obj, idx), "取消", null);
        }
    }

    private void saveScript(Map<String, String> values, JSONObject obj, int idx) {
        String name = safe(values.get("n")).trim();
        String ext = safe(values.get("e")).trim();
        String code = safe(values.get("c"));

        if (name.isEmpty()) {
            Dialogs.toast("名称不能为空");
            return;
        }
        try {
            JSONObject nobj = new JSONObject();
            nobj.put("name", name);
            nobj.put("ext", ext);
            nobj.put("code", code);
            nobj.put("trusted", obj != null && PreviewEngine.isScriptTrusted(obj));
            nobj.put("enabled", obj == null || PreviewEngine.isScriptEnabled(obj));
            if (obj != null) {
                if (obj.has("builtin")) nobj.put("builtin", JsonUtils.optBoolean(obj, "builtin", false));
                if (obj.has("builtin_id")) nobj.put("builtin_id", JsonUtils.optString(obj, "builtin_id", ""));
            }

            if (idx >= 0 && obj != null) ScriptRepo.replaceAt(idx, nobj);
            else ScriptRepo.add(nobj);

            Dialogs.toast("脚本已保存");
            recreateScreen();
        } catch (Throwable t) {
            Dialogs.toast("保存失败: " + t.getMessage());
        }
    }

    /** 静默保存（不刷新界面，供中立按钮复用） */
    private void saveScriptQuiet(Map<String, String> values, JSONObject obj, int idx) {
        String name = safe(values.get("n")).trim();
        if (name.isEmpty()) return;
        try {
            JSONObject nobj = new JSONObject();
            nobj.put("name", name);
            nobj.put("ext", safe(values.get("e")).trim());
            nobj.put("code", safe(values.get("c")));
            nobj.put("trusted", obj != null && PreviewEngine.isScriptTrusted(obj));
            nobj.put("enabled", obj == null || PreviewEngine.isScriptEnabled(obj));
            if (obj != null) {
                if (obj.has("builtin")) nobj.put("builtin", JsonUtils.optBoolean(obj, "builtin", false));
                if (obj.has("builtin_id")) nobj.put("builtin_id", JsonUtils.optString(obj, "builtin_id", ""));
            }
            if (idx >= 0 && obj != null) ScriptRepo.replaceAt(idx, nobj);
            else ScriptRepo.add(nobj);
        } catch (Throwable ignored) {}
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ============================================================
    // 对话框：新手引导
    // ============================================================

    private void showPreviewBeginnerGuide() {
        showDocDialog("预览文件怎么用",
                "【最短路径】\n"
                        + "1. 回到主页。\n"
                        + "2. 点「打开文件」选择要看的文件；或在文件管理器里用「打开方式 / 分享」。\n"
                        + "3. 内置脚本会自动按后缀匹配。\n"
                        + "4. 只看文件内容时，不需要开启可信增强、代理、MCP 或调试控制台。\n\n"
                        + "【空文件也应该有提示】\n"
                        + "0 字节文件不是错误。空文件应该显示空状态，而不是白屏。\n"
                        + "如果仍然白屏，先看「预览出问题了」，再执行「清理资源缓存」或「当前配置」体检。\n\n"
                        + "【什么时候需要改设置】\n"
                        + "只在三种情况下改：\n"
                        + "· 没有对应扩展名的脚本\n"
                        + "· 你要让脚本联网\n"
                        + "· 你要让脚本保存文件\n"
                        + "其他开关保持默认。");
    }

    private void showSoAnalysisBeginnerGuide() {
        showDocDialog("SO 分析怎么用",
                "【建议顺序】\n"
                        + "1. 先看「概览」：确认架构、入口、节区、导入导出数量。\n"
                        + "2. 再看「JNI 与 Native」：找 Java 层调用入口。\n"
                        + "3. 再看「字符串」和「网络与敏感信息」：找 URL、密钥痕迹。\n"
                        + "4. 最后看「反汇编」与「风险特征」。\n\n"
                        + "【别一上来就开高级能力】\n"
                        + "SO 静态分析默认不需要可信增强、网络代理或文件落盘。\n"
                        + "只有要调用 AI、联网工具、保存导出结果时再开启对应能力。\n\n"
                        + "【看不懂时先抓三个结论】\n"
                        + "· 这个 SO 是什么架构\n"
                        + "· 它暴露了哪些 JNI / 导出函数\n"
                        + "· 它有没有明显网络、加密、反调试、壳或敏感字符串");
    }

    private void showAiMcpBeginnerGuide() {
        boolean hasEndpoint = !Utils.isEmpty(sp.getString(Consts.PREF_KEY_SCRIPT_API_URL, ""));
        boolean hasModel = !Utils.isEmpty(sp.getString(Consts.PREF_KEY_SCRIPT_API_MODEL, ""));
        boolean hasKey = !Utils.isEmpty(sp.getString(Consts.PREF_KEY_SCRIPT_API_KEY, ""));

        showDocDialog("AI / MCP 新手路径",
                "【必须填的三样东西】\n"
                        + "API 端点：" + (hasEndpoint ? "已填 ✓" : "未填 ✗") + "\n"
                        + "模型 ID：" + (hasModel ? "已填 ✓" : "未填 ✗") + "\n"
                        + "API 密钥：" + (hasKey ? "已填 ✓" : "未填 ✗") + "\n\n"
                        + "【正确打开顺序】\n"
                        + "1. 先填 API 端点、密钥、模型（设置页「API 配置」）。\n"
                        + "2. 开启「启用 AI 接口」。\n"
                        + "3. 要用 Agent / MCP，再开启「MCP Agent」并配置服务列表。\n"
                        + "4. 要让脚本拿到密钥或保存能力，必须开启「可信脚本增强」并单独授权脚本。\n\n"
                        + "【最容易出错的地方】\n"
                        + "· 只打开 MCP Agent 但没填 API → AI 不会工作\n"
                        + "· 只填密钥但没给脚本可信授权 → 脚本拿不到密钥\n"
                        + "· 代理白名单写错 → MCP 服务连接失败");
    }

    private void showPanicHelp() {
        showDocDialog("故障救援",
                "【先按现象处理】\n"
                        + "白屏：先清理资源缓存，再重新打开文件；仍白屏就看「当前配置」。\n"
                        + "空文件：0 字节不是错误，应该显示空状态；如果空文件白屏，属于脚本或 WebView 加载失败。\n"
                        + "脚本无反应：开启「调试控制台」看日志；旧 WebView 再开「兼容模式」。\n"
                        + "AI 不工作：检查 API 配置、可信授权和代理开关。\n\n"
                        + "【不知道自己改了什么】\n"
                        + "回到设置页点「恢复新手默认设置」。\n"
                        + "它会关闭高风险开关、恢复内置脚本、清理常见干扰，"
                        + "但不会删除自定义脚本和已保存的 API 信息。");
    }

    private void confirmResetBeginnerDefaults() {
        new Dialogs.Builder()
                .setTitle("恢复新手默认设置")
                .setMessage("这会关闭可信增强、代理、MCP Agent、调试控制台、兼容模式和外部联动，"
                        + "恢复内置脚本并清理缓存。\n\n"
                        + "不会删除自定义脚本、API 地址、模型、密钥、MCP 服务列表和导出目录。")
                .setPositiveButton("恢复", (d, w) -> {
                    sp.edit()
                            .putBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false)
                            .putBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false)
                            .putBoolean(Consts.PREF_KEY_MCP_AGENT_ENABLED, false)
                            .putBoolean(Consts.PREF_KEY_DEBUG_CONSOLE, false)
                            .putBoolean(Consts.PREF_KEY_COMPAT_MODE, false)
                            .putBoolean(Consts.PREF_KEY_DARK_THEME, false)
                            .putBoolean(Consts.PREF_KEY_SHOW_ADVANCED_SETTINGS, false)
                            .putBoolean(Consts.PREF_KEY_BROWSER_TOP_BAR_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AUTO_CLEANUP_SESSIONS, true)
                            .putBoolean(Consts.PREF_KEY_SCRIPT_SAVE_ENABLED, true)
                            .putBoolean("ext_open_enabled", false)
                            .putBoolean(Consts.PREF_KEY_AGENT_PLANNER_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AGENT_FILE_CONTEXT_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AGENT_VISION_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AGENT_MEMORY_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AGENT_HISTORY_ENABLED, true)
                            .putBoolean(Consts.PREF_KEY_AGENT_AUTO_MEMORY_ENABLED, true)
                            .putInt(Consts.PREF_KEY_SCRIPTS_VERSION, 0)
                            .remove(Consts.PREF_KEY_ACTIVE_TRUSTED_SCRIPT)
                            .apply();
                    PreviewEngine.ensureDefaultScripts(sp);
                    clearCache();
                    Dialogs.toast("已恢复新手默认设置");
                    recreateScreen();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 通用文档对话框 */
    private void showDocDialog(String title, String content) {
        new Dialogs.Builder()
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("关闭", null)
                .show();
    }

    // ============================================================
    // 对话框：协议 / 关于
    // ============================================================

    private void showEulaViewer() {
        new Dialogs.Builder()
                .setTitle("使用说明与免责协议")
                .setMessage("本应用为本地文件预览工具，所有解析均在设备本地完成。\n\n"
                        + "一、基础使用\n"
                        + "· 打开文件：主页「打开文件」，或任意文件管理器「打开方式 / 分享」\n"
                        + "· 进入设置：主页右上角齿轮图标\n"
                        + "· 添加脚本：设置页「快速添加脚本」\n\n"
                        + "二、核心功能\n"
                        + "基于自定义脚本的多功能文件预览工具，支持 SO、Markdown、SQL、PDF 等格式，"
                        + "并允许通过 JavaScript 脚本扩展更多预览能力。\n\n"
                        + "三、安全与增强\n"
                        + "1. 默认仅开放基础预览能力。\n"
                        + "2. 脚本需访问网络时，开启「网络请求代理」。\n"
                        + "3. 需本地落盘或敏感 API 密钥时，必须主动开启「可信脚本增强」并单独授权脚本。\n"
                        + "4. 仅对能审计且完全信任的脚本开启增强能力。\n\n"
                        + "四、免责与责任边界\n"
                        + "本应用仅提供本地预览、分析与受控扩展能力，不承诺适配所有第三方脚本、页面或接口。"
                        + "若因导入不可信脚本、访问不可信网络目标、填写高敏感密钥或其他不当使用造成损失，"
                        + "使用者需自行承担风险。\n\n"
                        + "本项目基于 SOPreviewer（AGPL-3.0）改造成独立应用。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showEulaDialog(Runnable onAgree, Runnable onDisagree) {
        new Dialogs.Builder()
                .setTitle("使用说明与免责协议")
                .setMessage("请先阅读以下重点条款，特别是涉及脚本执行、敏感能力、网络访问与责任边界的内容。\n\n"
                        + "1. 本应用为本地文件预览工具，解析均在设备本地完成。\n"
                        + "2. 预览脚本为本地 JavaScript，默认无网络与文件写入权限。\n"
                        + "3. 开启 AI / MCP / 代理等联网能力将发送数据至你配置的第三方服务。\n"
                        + "4. 请勿使用本工具处理来源不明的敏感文件。\n"
                        + "5. 因导入不可信脚本或不当使用造成的损失，由使用者自行承担。\n\n"
                        + "本项目基于 SOPreviewer（AGPL-3.0）改造成独立应用。")
                .setCancelable(false)
                .setPositiveButton("同意并继续", (d, w) -> {
                    sp.edit().putBoolean(Consts.PREF_KEY_EULA_AGREED, true).apply();
                    if (onAgree != null) onAgree.run();
                })
                .setNegativeButton("不同意", (d, w) -> {
                    if (onDisagree != null) onDisagree.run();
                })
                .show();
    }

    private void showOpenSourceDialog() {
        showDocDialog("开源协议",
                "本应用基于 SOPreviewer 改造，遵循 GNU Affero General Public License v3.0。\n\n"
                        + "【协议摘要】\n"
                        + "你可以在遵守 AGPL-3.0 的前提下使用、修改和分发本项目，"
                        + "但必须保留原始版权与许可声明。\n\n"
                        + "AGPL-3.0 特别要求：如果你通过网络提供服务的方式使用本项目，"
                        + "也必须向使用者提供完整源代码。");
    }

    private void showLegalDialog() {
        showDocDialog("开源与免责声明",
                "【许可证】\n"
                        + "GNU Affero General Public License v3.0 (AGPL-3.0)\n"
                        + "你可以在遵守协议的前提下使用、修改和分发本项目，"
                        + "但必须保留原始版权与许可声明。\n\n"
                        + "【源码仓库】\n"
                        + "https://github.com/xiaomengXM/SOViewer-App\n"
                        + "（原始项目：https://github.com/bilieebiliee1-design/SOPreviewer）\n\n"
                        + "【责任边界】\n"
                        + "本应用仅提供本地预览、分析与受控扩展能力，不承诺适配所有第三方脚本、"
                        + "页面或接口。因导入不可信脚本、访问不可信网络目标、填写高敏感密钥"
                        + "或其他不当使用造成的任何损失，使用者需自行承担风险。");
    }

    // ============================================================
    // 对话框：API 配置
    // ============================================================

    private void showApiDialog() {
        new FormDialog(requireContext())
                .title("配置 API 全局变量")
                .desc("用于配置通用 API 地址、模型 ID 和密钥。敏感能力不会直接对所有脚本开放。")
                .input("url", "API 链接 (完整 URL)",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_URL, ""), "https://...")
                .input("model", "模型 ID",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_MODEL, ""), "gpt-4.1 / deepseek-chat ...")
                .password("key", "API 密钥",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_KEY, ""), "sk-...")
                .note("安全提示",
                        "默认最小权限模式下，脚本只能读取 API 地址与模型信息，无法获得密钥本体。"
                                + "只有在全局开启「可信脚本增强」且当前脚本被单独标记为可信时，"
                                + "脚本才能读取密钥。")
                .show("保存", values -> {
                    sp.edit()
                            .putString(Consts.PREF_KEY_SCRIPT_API_URL, safe(values.get("url")).trim())
                            .putString(Consts.PREF_KEY_SCRIPT_API_MODEL, safe(values.get("model")).trim())
                            .putString(Consts.PREF_KEY_SCRIPT_API_KEY, safe(values.get("key")).trim())
                            .apply();
                    Dialogs.toast("API 配置已保存");
                    recreateScreen();
                }, "取消", null);
    }

    // ============================================================
    // 对话框：MCP Agent 配置
    // ============================================================

    private static final String DEFAULT_MCP_SAMPLE =
            "[\n"
                    + "  {\n"
                    + "    \"name\": \"remote-tools\",\n"
                    + "    \"url\": \"https://example.com/mcp\",\n"
                    + "    \"type\": \"streamable-http\",\n"
                    + "    \"enabled\": true,\n"
                    + "    \"headers\": {\n"
                    + "      \"Authorization\": \"Bearer xxx\"\n"
                    + "    }\n"
                    + "  }\n"
                    + "]";

    private void showMcpAgentDialog() {
        new FormDialog(requireContext())
                .title("MCP Agent 配置")
                .desc("用于连续对话、任务编排、MCP 工具调用、文件/图片上下文和本地记忆。")
                .input("agent_api_url", "API 端点",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_URL, ""), "https://.../v1/chat/completions")
                .password("agent_api_key", "API 密钥",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_KEY, ""), "sk-...")
                .input("agent_model", "默认模型 ID",
                        sp.getString(Consts.PREF_KEY_SCRIPT_API_MODEL, ""), "gpt-4.1 / deepseek-chat")
                .input("planner_model", "规划模型 ID（留空用默认）",
                        sp.getString(Consts.PREF_KEY_AGENT_PLANNER_MODEL, ""), "适合拆解任务的模型")
                .input("executor_model", "执行模型 ID（留空用默认）",
                        sp.getString(Consts.PREF_KEY_AGENT_EXECUTOR_MODEL, ""), "适合工具调用的模型")
                .input("vision_model", "视觉模型 ID（留空用默认）",
                        sp.getString(Consts.PREF_KEY_AGENT_VISION_MODEL, ""), "支持图片输入的模型")
                .multiline("mcp_servers", "MCP 服务列表 JSON",
                        sp.getString(Consts.PREF_KEY_MCP_SERVERS_JSON, "[]"), DEFAULT_MCP_SAMPLE)
                .input("mcp_sdk", "MCP TypeScript SDK ESM 地址",
                        sp.getString(Consts.PREF_KEY_MCP_SDK_BASE_URL, DEFAULT_MCP_SDK),
                        DEFAULT_MCP_SDK)
                .multiline("agent_prompt", "系统提示词",
                        sp.getString(Consts.PREF_KEY_AGENT_SYSTEM_PROMPT, ""), "自定义 Agent 行为")
                .input("reasoning_effort", "推理强度",
                        sp.getString(Consts.PREF_KEY_AGENT_REASONING_EFFORT, "medium"), "low / medium / high")
                .input("agent_rounds", "最大工具循环轮数",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_AGENT_MAX_ROUNDS, 6, 1, 20)),
                        "1-20，建议 6")
                .input("agent_temperature", "Temperature",
                        sp.getString(Consts.PREF_KEY_AGENT_TEMPERATURE, "0.2"), "0-2，建议 0.2")
                .input("agent_tokens", "最大输出 Token",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_AGENT_MAX_TOKENS, 2048, 128, 32768)),
                        "128-32768")
                .input("file_chars", "文件上下文上限（字符）",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_AGENT_MAX_FILE_CHARS, 12000, 0, 200000)),
                        "0 表示不注入")
                .input("memory_items", "长期记忆上限（条）",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_AGENT_MEMORY_MAX_ITEMS, 80, 0, 1000)),
                        "例如 80")
                .show("保存", this::saveMcpConfig, "取消", null);
    }

    private static final String DEFAULT_MCP_SDK =
            "https://esm.sh/@modelcontextprotocol/sdk@1.29.0";

    private void saveMcpConfig(Map<String, String> v) {
        String apiUrl = safe(v.get("agent_api_url")).trim();
        String model = safe(v.get("agent_model")).trim();
        String sdk = safe(v.get("mcp_sdk")).trim();
        String reasoning = safe(v.get("reasoning_effort")).trim().toLowerCase();

        if (!apiUrl.isEmpty() && !apiUrl.matches("^https?://.*")) {
            Dialogs.toast("API 端点必须是 http/https URL");
            return;
        }
        if (!sdk.isEmpty() && !sdk.matches("^https?://.*")) {
            Dialogs.toast("MCP SDK 地址必须是 http/https URL");
            return;
        }
        if (sdk.isEmpty()) sdk = DEFAULT_MCP_SDK;
        if (!"low".equals(reasoning) && !"medium".equals(reasoning) && !"high".equals(reasoning)) {
            reasoning = "medium";
        }

        String serversRaw = safe(v.get("mcp_servers")).trim();
        try {
            new JSONArray(serversRaw.isEmpty() ? "[]" : serversRaw);
        } catch (Throwable t) {
            Dialogs.toast("MCP 服务 JSON 无效");
            return;
        }

        boolean apiEnabled = !apiUrl.isEmpty() || !model.isEmpty();
        sp.edit()
                .putBoolean(Consts.PREF_KEY_SCRIPT_API_ENABLED, apiEnabled)
                .putString(Consts.PREF_KEY_SCRIPT_API_URL, apiUrl)
                .putString(Consts.PREF_KEY_SCRIPT_API_KEY, safe(v.get("agent_api_key")).trim())
                .putString(Consts.PREF_KEY_SCRIPT_API_MODEL, model)
                .putString(Consts.PREF_KEY_AGENT_PLANNER_MODEL, safe(v.get("planner_model")).trim())
                .putString(Consts.PREF_KEY_AGENT_EXECUTOR_MODEL, safe(v.get("executor_model")).trim())
                .putString(Consts.PREF_KEY_AGENT_VISION_MODEL, safe(v.get("vision_model")).trim())
                .putString(Consts.PREF_KEY_MCP_SERVERS_JSON, serversRaw.isEmpty() ? "[]" : serversRaw)
                .putString(Consts.PREF_KEY_MCP_SDK_BASE_URL, sdk)
                .putString(Consts.PREF_KEY_AGENT_SYSTEM_PROMPT, safe(v.get("agent_prompt")).trim())
                .putString(Consts.PREF_KEY_AGENT_REASONING_EFFORT, reasoning)
                .putString(Consts.PREF_KEY_AGENT_MAX_ROUNDS,
                        String.valueOf(Utils.parseBoundedInt(safe(v.get("agent_rounds")), 6, 1, 20)))
                .putString(Consts.PREF_KEY_AGENT_TEMPERATURE,
                        String.valueOf(Utils.parseBoundedDouble(safe(v.get("agent_temperature")), 0.2, 0.0, 2.0)))
                .putString(Consts.PREF_KEY_AGENT_MAX_TOKENS,
                        String.valueOf(Utils.parseBoundedInt(safe(v.get("agent_tokens")), 2048, 128, 32768)))
                .putString(Consts.PREF_KEY_AGENT_MAX_FILE_CHARS,
                        String.valueOf(Utils.parseBoundedInt(safe(v.get("file_chars")), 12000, 0, 200000)))
                .putString(Consts.PREF_KEY_AGENT_MEMORY_MAX_ITEMS,
                        String.valueOf(Utils.parseBoundedInt(safe(v.get("memory_items")), 80, 0, 1000)))
                .apply();

        Dialogs.toast("MCP Agent 配置已保存");
        recreateScreen();
    }

    // ============================================================
    // 对话框：高级策略
    // ============================================================

    private void showAdvancedPolicyDialog() {
        new FormDialog(requireContext())
                .title("高级策略")
                .desc("这些设置会直接影响大文件预览、代理访问边界和缓存占用。")
                .input("large_mb", "大文件预警阈值 (MB)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_LARGE_FILE_WARNING_MB, 50, 1, 2048)),
                        "例如 50")
                .input("cache_mb", "缓存上限 (MB)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_CACHE_MAX_MB, 100, 0, 4096)),
                        "例如 100，0 表示不限制")
                .input("save_mb", "脚本保存上传上限 (MB)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_SAVE_MAX_MB, 80, 1, 512)),
                        "1-512，默认 80")
                .input("preload_mb", "单个外部资源预加载上限 (MB)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_PRELOAD_MAX_MB, 64, 1, 512)),
                        "1-512，默认 64")
                .input("proxy_mb", "代理响应上限 (MB)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB, 32, 1, 512)),
                        "1-512，默认 32")
                .input("ttl_min", "预览会话有效期 (分钟)",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_SESSION_TTL_MINUTES, 10, 1, 1440)),
                        "1-1440，默认 10")
                .input("port", "本地服务端口（0 自动分配）",
                        String.valueOf(Utils.getIntPref(sp, Consts.PREF_KEY_SERVER_PORT_HINT, 0, 0, 65535)),
                        "留空自动分配")
                .multiline("allowlist", "代理白名单（每行一个域名，留空不限制）",
                        sp.getString(Consts.PREF_KEY_PROXY_ALLOWLIST, ""), "api.example.com")
                .show("保存", v -> {
                    sp.edit()
                            .putString(Consts.PREF_KEY_LARGE_FILE_WARNING_MB,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("large_mb")), 50, 1, 2048)))
                            .putString(Consts.PREF_KEY_CACHE_MAX_MB,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("cache_mb")), 100, 0, 4096)))
                            .putString(Consts.PREF_KEY_SAVE_MAX_MB,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("save_mb")), 80, 1, 512)))
                            .putString(Consts.PREF_KEY_PRELOAD_MAX_MB,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("preload_mb")), 64, 1, 512)))
                            .putString(Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("proxy_mb")), 32, 1, 512)))
                            .putString(Consts.PREF_KEY_SESSION_TTL_MINUTES,
                                    String.valueOf(Utils.parseBoundedInt(safe(v.get("ttl_min")), 10, 1, 1440)))
                            .putInt(Consts.PREF_KEY_SERVER_PORT_HINT,
                                    Utils.parseBoundedInt(safe(v.get("port")), 0, 0, 65535))
                            .putString(Consts.PREF_KEY_PROXY_ALLOWLIST, safe(v.get("allowlist")).trim())
                            .apply();
                    CacheManager.trimCacheToLimit();
                    Dialogs.toast("高级策略已保存");
                    recreateScreen();
                }, "取消", null);
    }

    // ============================================================
    // 对话框：保存目录
    // ============================================================

    private void showDirDialog() {
        boolean usePublic = sp.getBoolean("download_dir_use_public", true);
        String custom = sp.getString("download_dir_custom", "");

        new FormDialog(requireContext())
                .title("脚本文件保存目录设置")
                .desc("用于控制脚本生成文件时的默认落盘位置。")
                .note("目录说明",
                        "默认使用应用专属导出目录（沙箱内），无需存储权限，最安全。\n\n"
                                + "如果你希望导出的文件能被其他应用直接访问，可以指定一个绝对路径，"
                                + "例如 /storage/emulated/0/Download。该目录必须已存在且可写。")
                .note("注意事项",
                        "请输入绝对路径。留空表示继续使用应用专属目录。"
                                + "仅在「可信脚本增强」开启且当前脚本被授权后，脚本端才可实际写入。")
                .input("path", "自定义目录", usePublic ? "" : custom, "/storage/emulated/0/Download")
                .show("保存自定义目录", v -> {
                    String p = safe(v.get("path")).trim();
                    if (p.isEmpty() || !new File(p).isAbsolute()) {
                        Dialogs.toast("请填写有效绝对路径");
                        return;
                    }
                    try {
                        Utils.validateWritableDirectory(new File(p), true);
                    } catch (Throwable t) {
                        Dialogs.toast(t.getMessage() == null ? "目录校验失败" : t.getMessage());
                        return;
                    }
                    sp.edit().putBoolean("download_dir_use_public", false)
                            .putString("download_dir_custom", p).apply();
                    Dialogs.toast("已保存自定义目录");
                    recreateScreen();
                }, "使用默认目录", () -> {
                    sp.edit().remove("download_dir_custom")
                            .putBoolean("download_dir_use_public", true).apply();
                    Dialogs.toast("已切换为应用默认导出目录");
                    recreateScreen();
                });
    }

    // ============================================================
    // 对话框：外部联动辅助应用
    // ============================================================

    private void showExtDialog() {
        boolean installed = Utils.isPackageInstalled("com.viewer.so.app");
        new Dialogs.Builder()
                .setTitle("辅助应用")
                .setMessage(installed
                        ? "辅助应用已安装。\n\n它可以让你从其他文件管理器的「打开方式」或「分享」直接唤起本应用预览文件。"
                        : "当前版本已内置该能力，无需额外安装辅助应用 —— "
                        + "「打开方式 / 分享」入口已在主应用内注册，可直接使用。")
                .setPositiveButton("知道了", null)
                .show();
    }

    // ============================================================
    // 缓存管理
    // ============================================================

    private void showCacheMgmt() {
        File dir = Utils.webCacheDir();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Dialogs.toast("当前没有缓存文件");
            return;
        }

        final File[] list = files;
        String[] names = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            names[i] = list[i].getName() + "  (" + Utils.humanSize(list[i].length()) + ")";
        }

        new Dialogs.Builder()
                .setTitle("全局缓存管理 (" + list.length + " 个文件)")
                .setItems(names, (d, w) -> showCacheItemMenu(list[w]))
                .show();
    }

    private void showCacheItemMenu(File f) {
        new Dialogs.Builder()
                .setTitle(f.getName())
                .setItems(new String[]{"查看详情", "删除文件"}, (d, w) -> {
                    if (w == 0) {
                        new Dialogs.Builder()
                                .setTitle("缓存详情")
                                .setMessage("文件名：" + f.getName() + "\n"
                                        + "大小：" + f.length() + " 字节（" + Utils.humanSize(f.length()) + "）\n"
                                        + "路径：" + f.getAbsolutePath())
                                .setPositiveButton("确定", null)
                                .show();
                    } else {
                        Dialogs.toast(f.delete() ? "已删除" : "删除失败");
                        recreateScreen();
                    }
                })
                .show();
    }

    private void clearCache() {
        File[] fs = Utils.webCacheDir().listFiles();
        long bytes = 0;
        int count = 0;
        if (fs != null) {
            for (File f : fs) {
                bytes += f.length();
                if (f.delete()) count++;
            }
        }
        Dialogs.toast(String.format("已清理 %d 个文件，共释放 %.2f MB 空间",
                count, bytes / 1048576.0));
    }

    // ============================================================
    // 脚本备份与导入
    // ============================================================

    private void showScriptBackupDialog() {
        String current = ScriptRepo.all(sp).toString();

        new FormDialog(requireContext())
                .title("脚本备份与导入")
                .desc("下方是当前脚本配置 JSON。你可以复制保存，也可以粘贴备份后点击「导入覆盖」。")
                .multiline("scripts_json", null, current, "粘贴脚本 JSON")
                .note("导入说明", "导入会覆盖当前脚本列表。建议先复制当前配置作为备份。")
                .show("导入覆盖", v -> {
                    String raw = safe(v.get("scripts_json")).trim();
                    try {
                        JSONArray imported = new JSONArray(raw);
                        for (int i = 0; i < imported.length(); i++) {
                            JSONObject s = imported.optJSONObject(i);
                            if (s == null
                                    || Utils.isEmpty(JsonUtils.optString(s, "name", ""))
                                    || Utils.isEmpty(JsonUtils.optString(s, "code", ""))) {
                                Dialogs.toast("导入失败：第 " + (i + 1) + " 个脚本缺少 name 或 code");
                                return;
                            }
                        }
                        ScriptRepo.save(imported);
                        Dialogs.toast("脚本配置已导入");
                        recreateScreen();
                    } catch (Throwable t) {
                        Dialogs.toast("导入失败：JSON 格式不正确");
                    }
                },
                        "保存导出", v -> exportScriptsJson(safe(v.get("scripts_json"))),
                        "关闭", null);
    }

    private void exportScriptsJson(String raw) {
        App.io().submit(() -> {
            try {
                File dir = Utils.getPreferredDownloadDirectory();
                String fileName = Utils.buildUniqueName(dir, "so_viewer_scripts.json");
                File target = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    fos.write(raw.getBytes(StandardCharsets.UTF_8));
                }
                Dialogs.toast("已导出到: " + target.getAbsolutePath());
            } catch (Throwable t) {
                Dialogs.toast("导出失败: " + (t.getMessage() == null ? t.toString() : t.getMessage()));
            }
        });
    }

    // ============================================================
    // 配置体检
    // ============================================================

    private void showEnvCheck() {
        JSONArray scripts = ScriptRepo.all(sp);
        int[] c = ScriptRepo.count(scripts);

        boolean trustedMode = sp.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false);
        boolean proxyEnabled = sp.getBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false);
        String allowlist = sp.getString(Consts.PREF_KEY_PROXY_ALLOWLIST, "").trim();

        StringBuilder advice = new StringBuilder();
        if (scripts.length() == 0) {
            advice.append("1. 当前没有任何脚本，请先添加脚本或重置内置脚本。\n\n");
        }
        if (trustedMode && c[2] == 0) {
            advice.append("2. 已开启可信增强，但还没有单独授权脚本；敏感能力不会实际注入。\n\n");
        }
        if (proxyEnabled && allowlist.isEmpty()) {
            advice.append("3. 代理已开启但未配置白名单；所有脚本都可代理访问公网域名，"
                    + "建议为常用 API 域名设置白名单。\n\n");
        }
        if (sp.getBoolean(Consts.PREF_KEY_SCRIPT_API_ENABLED, false)
                && Utils.isEmpty(sp.getString(Consts.PREF_KEY_SCRIPT_API_KEY, ""))) {
            advice.append("4. API 配置已开启，但密钥为空；AI/MCP 类脚本可能无法完成请求。\n\n");
        }
        if (advice.length() == 0) {
            advice.append("当前没有发现明显配置冲突。\n"
                    + "若预览异常，优先检查脚本代码、缓存资源和 WebView 兼容性。");
        }

        int sdk = android.os.Build.VERSION.SDK_INT;

        new Dialogs.Builder()
                .setTitle("配置体检")
                .setMessage("这里汇总当前策略、脚本状态和运行环境，用于快速判断设置是否合理。\n\n"
                        + "【运行环境】\n"
                        + "Android 版本：" + android.os.Build.VERSION.RELEASE + " (API " + sdk + ")\n"
                        + "ES6 支持预估：" + (sdk >= 24 ? "支持" : "可能不支持") + "\n\n"
                        + "【脚本状态】\n"
                        + "共 " + scripts.length() + " 个，启用 " + c[3] + " 个\n"
                        + "内置 " + c[0] + " 个 · 自定义 " + c[1] + " 个 · 可信授权 " + c[2] + " 个\n\n"
                        + "【能力开关】\n"
                        + (trustedMode ? "可信增强开启" : "最小权限")
                        + " · " + (proxyEnabled ? "代理开启" : "代理关闭")
                        + " · " + (sp.getBoolean(Consts.PREF_KEY_SCRIPT_SAVE_ENABLED, true)
                                ? "允许落盘" : "禁止落盘") + "\n"
                        + "AI 接口：" + (sp.getBoolean(Consts.PREF_KEY_SCRIPT_API_ENABLED, false)
                                ? "已开启" : "未开启") + "\n"
                        + "MCP Agent：" + (sp.getBoolean(Consts.PREF_KEY_MCP_AGENT_ENABLED, false)
                                ? "已开启" : "未开启") + "\n\n"
                        + "【策略】\n"
                        + buildAdvancedPolicySummary() + "\n"
                        + "导出目录：" + Utils.getPreferredDownloadDirectoryDisplay() + "\n\n"
                        + "【建议】\n"
                        + advice)
                .setPositiveButton("确定", null)
                .show();
    }

    // ============================================================
    // 偏好变更监听
    // ============================================================

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key == null) return true;

        // 「显示高级设置」切换后立即重建页面（对应原版 onPreferenceChange → recreate）
        if (Consts.PREF_KEY_SHOW_ADVANCED_SETTINGS.equals(key)) {
            getPreferenceManager().getSharedPreferences()
                    .edit().putBoolean(key, Boolean.TRUE.equals(newValue)).apply();
            restartWithDelay();
            return true;
        }

        // 外部联动开关需要同步摘要
        if ("ext_open_enabled".equals(key)) {
            restartWithDelay();
            return true;
        }

        return true;
    }

    /** 延迟重建设置页，避开当前点击事件栈 */
    private void restartWithDelay() {
        final android.view.View root = getView();
        if (root != null) {
            root.post(this::recreateScreen);
        } else {
            recreateScreen();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 返回设置页时刷新动态摘要
        if (getPreferenceScreen() != null
                && sp.getBoolean(Consts.PREF_KEY_EULA_AGREED, false)) {
            syncSummary("action_cache",
                    "当前占用 " + Utils.humanSize(Utils.dirSize(Utils.webCacheDir())));
        }
    }
}