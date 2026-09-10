package com.viewer.so.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预览引擎：从 Core.java（186-750 行）移植的核心逻辑。
 *
 * 主要改动：
 *  - PluginContext → App.ctx() / Prefs / Dialogs
 *  - TextEditor   → 由调用方直接传入 fileName + File
 *  - openBuiltinBrowser → 回调 PreviewLauncher（由 Activity 实现）
 *  - bin.mt.json  → org.json（经 JsonUtils 包装）
 *
 * 业务逻辑（脚本匹配、会话管理、HTML 组装、权限注入）保持不变。
 */
public final class PreviewEngine {

    private PreviewEngine() {}

    // ============================================================
    // 会话
    // ============================================================

    public static final class PreviewSession {
        public String token;
        public String html;
        public File file;
        public String scriptName;
        public boolean trusted;
        public long timestamp = System.currentTimeMillis();
    }

    private static final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();
    private static volatile String cachedTemplateHtml = null;

    public static PreviewSession getSession(String token) {
        if (token == null) return null;
        return sessions.get(token);
    }

    public static int sessionCount() {
        return sessions.size();
    }

    public static void clearSessions() {
        sessions.clear();
    }

    /** 清理过期会话及其临时文件（对应原 cleanupExpiredSessions） */
    public static void cleanupExpiredSessions() {
        long ttl = getSessionTtlMillis();
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            PreviewSession s = e.getValue();
            if (now - s.timestamp > ttl) {
                if (s.file != null && s.file.getAbsolutePath().contains("temp_received")) {
                    //noinspection ResultOfMethodCallIgnored
                    s.file.delete();
                }
                return true;
            }
            return false;
        });
    }

    private static PreviewSession getValidSession(String token) {
        if (Utils.isEmpty(token)) return null;
        PreviewSession s = sessions.get(token.trim());
        if (s == null) return null;
        if (System.currentTimeMillis() - s.timestamp > getSessionTtlMillis()) {
            sessions.remove(token.trim());
            if (s.file != null && s.file.getAbsolutePath().contains("temp_received")) {
                //noinspection ResultOfMethodCallIgnored
                s.file.delete();
            }
            return null;
        }
        s.timestamp = System.currentTimeMillis();
        return s;
    }

    private static long getSessionTtlMillis() {
        int min = Prefs.getInt(Consts.PREF_KEY_SESSION_TTL_MINUTES,
                Consts.DEFAULT_SESSION_TTL_MINUTES, 1, 1440);
        return min * 60_000L;
    }

    // ============================================================
    // 内置脚本表（原文照搬，含内联 JS）
    // ============================================================

    public static final class BuiltinScriptSpec {
        final String id, name, ext, assetFile, inlineCode;
        BuiltinScriptSpec(String id, String name, String ext, String assetFile, String inlineCode) {
            this.id = id; this.name = name; this.ext = ext;
            this.assetFile = assetFile; this.inlineCode = inlineCode;
        }
    }

    public static final BuiltinScriptSpec[] BUILTIN_SCRIPT_SPECS = {
            new BuiltinScriptSpec(Consts.BUILTIN_ID_HTML, "HTML 预览", "html,htm", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const frame = document.createElement('iframe');\n" +
                    "    frame.setAttribute('sandbox', 'allow-same-origin');\n" +
                    "    frame.style.width = '100%';\n" +
                    "    frame.style.minHeight = '100vh';\n" +
                    "    frame.style.border = '0';\n" +
                    "    frame.srcdoc = new TextDecoder().decode(content);\n" +
                    "    return frame;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_TEXT, "文本预览", "txt", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const pre = document.createElement('pre');\n" +
                    "    pre.style.whiteSpace = 'pre-wrap';\n" +
                    "    pre.style.wordWrap = 'break-word';\n" +
                    "    pre.textContent = new TextDecoder().decode(content);\n" +
                    "    return pre;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_SO, "SO 高级预览", "so", "so_viewer.js", null),
            new BuiltinScriptSpec(Consts.BUILTIN_ID_MD, "Markdown 预览", "md,markdown", "md_viewer.js", null),
            new BuiltinScriptSpec(Consts.BUILTIN_ID_SQLITE, "数据库预览", "db,sqlite,sqlite3", "sqlite_viewer.js", null),
            new BuiltinScriptSpec(Consts.BUILTIN_ID_PDF, "PDF 预览", "pdf", "pdf_viewer.js", null),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_JSON, "JSON 预览", "json", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const root = document.createElement('div');\n" +
                    "    root.style.cssText = 'height:100vh;overflow:auto;background:#f6f8fa;color:#24292f;font:13px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;padding:16px;box-sizing:border-box;';\n" +
                    "    const pre = document.createElement('pre');\n" +
                    "    pre.style.cssText = 'margin:0;white-space:pre-wrap;word-break:break-word;';\n" +
                    "    try { pre.textContent = JSON.stringify(JSON.parse(new TextDecoder().decode(content)), null, 2); }\n" +
                    "    catch (e) { pre.textContent = new TextDecoder().decode(content); }\n" +
                    "    root.appendChild(pre);\n" +
                    "    return root;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_XML, "XML 预览", "xml,svg,plist", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const root = document.createElement('div');\n" +
                    "    root.style.cssText = 'height:100vh;overflow:auto;background:#fff;color:#24292f;font:13px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;padding:16px;box-sizing:border-box;';\n" +
                    "    const raw = new TextDecoder().decode(content);\n" +
                    "    const pre = document.createElement('pre');\n" +
                    "    pre.style.cssText = 'margin:0;white-space:pre-wrap;word-break:break-word;';\n" +
                    "    try { pre.textContent = new XMLSerializer().serializeToString(new DOMParser().parseFromString(raw, 'application/xml')).replace(/></g, '>\\n<'); }\n" +
                    "    catch (e) { pre.textContent = raw; }\n" +
                    "    root.appendChild(pre);\n" +
                    "    return root;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_CSV, "CSV/表格预览", "csv,tsv", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const text = new TextDecoder().decode(content);\n" +
                    "    const sep = fileExt.toLowerCase() === 'tsv' ? '\\t' : ',';\n" +
                    "    const rows = text.split(/\\r?\\n/).filter(Boolean).slice(0, 2000).map(r => r.split(sep));\n" +
                    "    const wrap = document.createElement('div'); wrap.style.cssText='height:100vh;overflow:auto;background:#fff;padding:12px;box-sizing:border-box;';\n" +
                    "    const table = document.createElement('table'); table.style.cssText='border-collapse:collapse;font:13px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;';\n" +
                    "    rows.forEach((row, i)=>{ const tr=document.createElement('tr'); row.forEach(cell=>{ const el=document.createElement(i===0?'th':'td'); el.textContent=cell; el.style.cssText='border:1px solid #d0d7de;padding:6px 8px;max-width:360px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'; tr.appendChild(el); }); table.appendChild(tr); });\n" +
                    "    wrap.appendChild(table); return wrap;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_IMAGE, "图片预览", "png,jpg,jpeg,gif,webp,bmp", null,
                    "function render(fileName, fileExt, content) {\n" +
                    "    const root = document.createElement('div'); root.style.cssText='height:100vh;display:flex;align-items:center;justify-content:center;background:#111;overflow:auto;';\n" +
                    "    const blob = new Blob([content], {type:'image/' + (fileExt === 'jpg' ? 'jpeg' : fileExt)});\n" +
                    "    const img = document.createElement('img'); img.src = URL.createObjectURL(blob); img.style.cssText='max-width:100%;max-height:100%;object-fit:contain;';\n" +
                    "    root.appendChild(img); return root;\n}"),

            new BuiltinScriptSpec(Consts.BUILTIN_ID_AGENT, "MCP Agent 对话", "agent,mcpchat,chat", "agent_viewer.js", null)
    };

    // ============================================================
    // 内置脚本初始化
    // ============================================================

    /** 对应原 ensureDefaultScripts：版本升级时重建内置脚本，保留用户自定义脚本 */
    public static void ensureDefaultScripts(SharedPreferences prefs) {
        try {
            int storedVersion = prefs.getInt(Consts.PREF_KEY_SCRIPTS_VERSION, 0);
            if (storedVersion >= Consts.CURRENT_SCRIPTS_VERSION) return;

            JSONArray existing = JsonUtils.parseArray(prefs.getString(Consts.PREF_KEY_SCRIPTS, "[]"));
            JSONArray rebuilt = new JSONArray();
            List<String> builtinIds = new ArrayList<>();

            for (BuiltinScriptSpec spec : BUILTIN_SCRIPT_SPECS) {
                builtinIds.add(spec.id);

                // 保留用户对该内置脚本的自定义（仅 enabled / trusted 这类状态）
                boolean enabled = true;
                boolean trusted = false;
                for (int i = 0; i < existing.length(); i++) {
                    JSONObject o = existing.optJSONObject(i);
                    if (o == null) continue;
                    if (spec.id.equals(JsonUtils.optString(o, "id", ""))) {
                        enabled = JsonUtils.optBoolean(o, "enabled", true);
                        trusted = JsonUtils.optBoolean(o, "trusted", false);
                        break;
                    }
                }

                JSONObject obj = new JSONObject();
                obj.put("id", spec.id);
                obj.put("name", spec.name);
                obj.put("ext", spec.ext);
                obj.put("enabled", enabled);
                obj.put("trusted", trusted);
                obj.put("builtin", true);
                if (spec.assetFile != null) {
                    String code = readAsset(spec.assetFile);
                    obj.put("code", code != null ? code : "");
                } else {
                    obj.put("code", spec.inlineCode != null ? spec.inlineCode : "");
                }
                rebuilt.put(obj);
            }

            // 追加用户自定义（非内置）脚本
            for (int i = 0; i < existing.length(); i++) {
                JSONObject o = existing.optJSONObject(i);
                if (o == null) continue;
                String id = JsonUtils.optString(o, "id", "");
                if (!builtinIds.contains(id)) {
                    rebuilt.put(o);
                }
            }

            prefs.edit()
                    .putString(Consts.PREF_KEY_SCRIPTS, rebuilt.toString())
                    .putInt(Consts.PREF_KEY_SCRIPTS_VERSION, Consts.CURRENT_SCRIPTS_VERSION)
                    .apply();
        } catch (Throwable ignored) {
            // 初始化失败不阻断预览流程
        }
    }

    private static String readAsset(String name) {
        try (java.io.InputStream is = App.ctx().getAssets().open(name)) {
            return Utils.readStreamToString(is);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String readAssetOrEmpty(String name) {
        String s = readAsset(name);
        return s == null ? "" : s;
    }

    // ============================================================
    // 脚本匹配
    // ============================================================

    public static boolean isScriptTrusted(JSONObject script) {
        return JsonUtils.optBoolean(script, "trusted", false);
    }

    public static boolean isScriptEnabled(JSONObject script) {
        return JsonUtils.optBoolean(script, "enabled", true);
    }

    /** 按扩展名筛选匹配的脚本列表 */
    public static List<JSONObject> matchScripts(String fileExt) {
        List<JSONObject> matched = new ArrayList<>();
        JSONArray scripts = JsonUtils.parseArray(Prefs.getString(Consts.PREF_KEY_SCRIPTS, "[]"));
        for (int i = 0; i < scripts.length(); i++) {
            JSONObject script = scripts.optJSONObject(i);
            if (script == null || !isScriptEnabled(script)) continue;
            for (String ext : JsonUtils.optString(script, "ext", "").split(",")) {
                if (ext.trim().equalsIgnoreCase(fileExt)) {
                    matched.add(script);
                    break;
                }
            }
        }
        return matched;
    }

    // ============================================================
    // 预览启动
    // ============================================================

    public interface PreviewLauncher {
        void launchPreview(String url, boolean showTopBar);
    }

    /**
     * 组装预览页并启动本地预览。
     * 对应原 previewFileSafe + executePreview + continueExecution 的合并流程。
     */
    public static void startPreview(JSONObject script, String fileName, File file, PreviewLauncher launcher) {
        App.io().submit(() -> {
            try {
                updateActiveTrustedScript(script);
                String scriptCode = JsonUtils.optString(script, "code", "");
                String scriptName = JsonUtils.optString(script, "name", "unknown");
                String scriptExt = JsonUtils.optString(script, "ext", "unknown");

                // 远程资源缓存替换
                CacheManager.PreloadResult pr = CacheManager.processScriptResources(scriptCode);
                String processedScriptCode = CacheManager.removeRedundantCachedLocalUrls(pr.processedCode);

                String babelLocalUrl = null;
                if (Prefs.getBoolean(Consts.PREF_KEY_COMPAT_MODE, false)) {
                    String localName = CacheManager.buildLocalCacheFileName(
                            Consts.BABEL_PRIMARY_URL, Consts.BABEL_RESOURCE_NAME);
                    babelLocalUrl = "/cache/" + localName;
                    try {
                        JSONObject babelObj = new JSONObject();
                        babelObj.put("original", Consts.BABEL_PRIMARY_URL);
                        babelObj.put("local", localName);
                        babelObj.put("name", Consts.BABEL_RESOURCE_NAME);
                        babelObj.put("minifiedPreferred", true);
                        babelObj.put("mergedMinified", false);
                        JSONArray cands = new JSONArray();
                        cands.put(Consts.BABEL_PRIMARY_URL);
                        cands.put(Consts.BABEL_FALLBACK_URL);
                        babelObj.put("candidates", cands);
                        pr.preloadUrls.put(babelObj);
                    } catch (Throwable ignored) {}
                }

                String html = buildPreviewHtml(script, processedScriptCode, fileName, file,
                        scriptExt, pr.preloadUrls.toString(), scriptName, babelLocalUrl);

                String token = UUID.randomUUID().toString();
                PreviewSession session = new PreviewSession();
                session.token = token;
                session.html = html;
                session.file = file;
                session.scriptName = scriptName;
                session.trusted = Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false)
                        && isScriptTrusted(script);

                sessions.put(token, session);
                scheduleCleanup();

                boolean showTopBar = Prefs.getBoolean(Consts.PREF_KEY_BROWSER_TOP_BAR_ENABLED, true);
                HttpServer.startIfNeeded(App.ctx(), () -> {
                    String url = "http://127.0.0.1:" + HttpServer.currentPort + "/preview/" + token;
                    if (launcher != null) launcher.launchPreview(url, showTopBar);
                });
            } catch (Throwable e) {
                Dialogs.toast("生成预览失败: " + e.getMessage());
            }
        });
    }

    /** 惰性清理：不在每页都全量扫描 */
    private static void scheduleCleanup() {
        if (sessions.size() > 8) {
            cleanupExpiredSessions();
        }
    }

    private static void updateActiveTrustedScript(JSONObject script) {
        String activeName = Prefs.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false)
                && isScriptTrusted(script)
                ? JsonUtils.optString(script, "name", "") : "";
        Prefs.putString(Consts.PREF_KEY_ACTIVE_TRUSTED_SCRIPT, activeName);
    }

    // ============================================================
    // HTML 组装
    // ============================================================

    public static String buildPreviewHtml(JSONObject script, String scriptCode, String fileName,
                                          File file, String scriptExt, String preloadUrlsJson,
                                          String scriptName, String babelLocalUrl) {
        String safeFileName = JsonUtils.escapeJs(fileName);
        String safeExt = JsonUtils.escapeJs(scriptExt);
        String safeScriptCode = scriptCode.replace("</script>", "<\\/script>");
        String safeScriptName = JsonUtils.escapeJs(scriptName);
        String token = UUID.randomUUID().toString();
        String pluginGlobalsScript = buildPluginGlobalsScript(script, token);

        String babelInjection = "";
        if (Prefs.getBoolean(Consts.PREF_KEY_COMPAT_MODE, false)) {
            babelInjection = "<script src=\"" + (babelLocalUrl != null ? babelLocalUrl : Consts.BABEL_PRIMARY_URL) + "\"></script>";
        }

        String template = getTemplate();

        // 计算注入点在模板中的行号，供 JS 侧定位用户脚本起始行
        int injectionIndex = template.indexOf("/* USER_SCRIPT_INJECTION_POINT */");
        int scriptOffset = -1;
        if (injectionIndex != -1) {
            String prefix = template.substring(0, injectionIndex);
            scriptOffset = 0;
            for (int i = 0; i < prefix.length(); i++) {
                if (prefix.charAt(i) == '\n') scriptOffset++;
            }
        }

        return template.replace("{{FILENAME}}", safeFileName)
                .replace("{{EXTENSION}}", safeExt)
                .replace("{{SCRIPT_OFFSET}}", String.valueOf(scriptOffset))
                .replace("<!-- BABEL_INJECTION_POINT -->", babelInjection)
                .replace("'/raw'", "'/raw/" + token + "'")
                .replace("/* PRELOAD_URLS_JSON */", preloadUrlsJson != null ? preloadUrlsJson : "[]")
                .replace("/* SCRIPT_NAME */", safeScriptName)
                .replace("/* PLUGIN_GLOBALS_INJECTION */", pluginGlobalsScript)
                .replace("/* USER_SCRIPT_INJECTION_POINT */", safeScriptCode);
    }

    /**
     * 注入给 JS 的全局配置（原 buildPluginGlobalsScript）。
     * 所有敏感项（apiKey）仅在 trusted 模式下注入——保持原安全设计。
     */
    private static String buildPluginGlobalsScript(JSONObject script, String sessionToken) {
        SharedPreferences prefs = App.prefs();
        boolean apiEnabled = prefs.getBoolean(Consts.PREF_KEY_SCRIPT_API_ENABLED, false);
        boolean globalTrustedMode = prefs.getBoolean(Consts.PREF_KEY_SCRIPT_TRUSTED_MODE, false);
        boolean scriptTrusted = isScriptTrusted(script);
        String scriptName = JsonUtils.optString(script, "name", "");
        boolean trustedMode = globalTrustedMode && scriptTrusted;

        String apiUrl = JsonUtils.escapeJsSingleQuoted(apiEnabled ? Prefs.getString(Consts.PREF_KEY_SCRIPT_API_URL, "").trim() : "");
        String modelId = JsonUtils.escapeJsSingleQuoted(apiEnabled ? Prefs.getString(Consts.PREF_KEY_SCRIPT_API_MODEL, "").trim() : "");
        String apiKey = JsonUtils.escapeJsSingleQuoted(apiEnabled && trustedMode ? Prefs.getString(Consts.PREF_KEY_SCRIPT_API_KEY, "").trim() : "");
        boolean proxyEnabled = prefs.getBoolean(Consts.PREF_KEY_SCRIPT_PROXY_ENABLED, false);
        boolean saveEnabled = trustedMode && prefs.getBoolean(Consts.PREF_KEY_SCRIPT_SAVE_ENABLED, true);
        String trustedScriptName = JsonUtils.escapeJsSingleQuoted(trustedMode ? scriptName : "");

        boolean mcpAgentEnabled = prefs.getBoolean(Consts.PREF_KEY_MCP_AGENT_ENABLED, false);
        String mcpServersJson = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_MCP_SERVERS_JSON, "[]").trim());
        String mcpSdkBaseUrl = JsonUtils.escapeJsSingleQuoted(
                Prefs.getString(Consts.PREF_KEY_MCP_SDK_BASE_URL, "https://esm.sh/@modelcontextprotocol/sdk@1.29.0").trim());

        String agentPrompt = JsonUtils.escapeJsSingleQuoted(
                Prefs.getString(Consts.PREF_KEY_AGENT_SYSTEM_PROMPT, defaultAgentPrompt()).trim());
        int agentMaxRounds = Prefs.getInt(Consts.PREF_KEY_AGENT_MAX_ROUNDS, 6, 1, 20);
        int agentMaxTokens = Prefs.getInt(Consts.PREF_KEY_AGENT_MAX_TOKENS, 2048, 128, 32768);
        int agentMaxFileChars = Prefs.getInt(Consts.PREF_KEY_AGENT_MAX_FILE_CHARS, 12000, 0, 200000);
        int agentMemoryMaxItems = Prefs.getInt(Consts.PREF_KEY_AGENT_MEMORY_MAX_ITEMS, 80, 0, 1000);

        long saveMaxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_SAVE_MAX_MB, Consts.DEFAULT_SAVE_MAX_MB, 1, 512);
        long preloadMaxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_PRELOAD_MAX_MB, Consts.DEFAULT_PRELOAD_MAX_MB, 1, 512);
        long proxyResponseMaxBytes = Prefs.getMbPrefBytes(Consts.PREF_KEY_PROXY_RESPONSE_MAX_MB, Consts.DEFAULT_PROXY_RESPONSE_MAX_MB, 1, 512);
        int sessionTtlMinutes = Prefs.getInt(Consts.PREF_KEY_SESSION_TTL_MINUTES, Consts.DEFAULT_SESSION_TTL_MINUTES, 1, 1440);

        String agentTemperature = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_AGENT_TEMPERATURE, "0.2").trim());
        String agentReasoningEffort = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_AGENT_REASONING_EFFORT, "medium").trim());
        String plannerModel = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_AGENT_PLANNER_MODEL, "").trim());
        String executorModel = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_AGENT_EXECUTOR_MODEL, "").trim());
        String visionModel = JsonUtils.escapeJsSingleQuoted(Prefs.getString(Consts.PREF_KEY_AGENT_VISION_MODEL, "").trim());

        boolean plannerEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_PLANNER_ENABLED, true);
        boolean fileContextEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_FILE_CONTEXT_ENABLED, true);
        boolean visionEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_VISION_ENABLED, true);
        boolean memoryEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_MEMORY_ENABLED, true);
        boolean historyEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_HISTORY_ENABLED, true);
        boolean autoMemoryEnabled = prefs.getBoolean(Consts.PREF_KEY_AGENT_AUTO_MEMORY_ENABLED, true);
        boolean darkTheme = prefs.getBoolean(Consts.PREF_KEY_DARK_THEME, false);
        boolean debugConsole = prefs.getBoolean(Consts.PREF_KEY_DEBUG_CONSOLE, false);
        String safeSessionToken = JsonUtils.escapeJsSingleQuoted(sessionToken == null ? "" : sessionToken);

        return "(function(){\n  const apiEnabled=" + apiEnabled + ";\n  const globalTrustedMode=" + globalTrustedMode + ";\n  const scriptTrusted=" + scriptTrusted + ";\n  const trustedMode=" + trustedMode + ";\n  const proxyEnabled=" + proxyEnabled + ";\n  const saveEnabled=" + saveEnabled + ";\n"
                + "  const apiUrl=apiEnabled?'" + apiUrl + "':'';\n  const modelId=apiEnabled?'" + modelId + "':'';\n"
                + "  const apiKey=(apiEnabled&&trustedMode)?'" + apiKey + "':'';\n  const sessionToken='" + safeSessionToken + "';\n  const proxyBase=proxyEnabled?(location.origin + '/proxy/') : '';\n"
                + "  const trustedScriptName=trustedMode?'" + trustedScriptName + "':'';\n"
                + "  let mcpServers=[]; try{mcpServers=JSON.parse('" + mcpServersJson + "'||'[]')||[]}catch(e){mcpServers=[];}\n"
                + "  window.SO_API_URL=apiUrl;\n  window.SO_MODEL_ID=modelId;\n  window.SO_API_KEY=apiKey;\n  window.SO_PROXY_BASE=proxyBase;\n"
                + "  window.SOViewerAPIConfig={enabled:apiEnabled,globalTrustedMode:globalTrustedMode,scriptTrusted:scriptTrusted,trustedMode:trustedMode,proxyEnabled:proxyEnabled,saveEnabled:saveEnabled,apiUrl:apiUrl,modelId:modelId,apiKey:apiKey,proxyBase:proxyBase,trustedScriptName:trustedScriptName};\n"
                + "  window.SOViewerSession={token:sessionToken,saveEndpoint:'/api/save_file?token='+encodeURIComponent(sessionToken),preloadEndpoint:'/api/preload?token='+encodeURIComponent(sessionToken)};\n"
                + "  window.SOViewerAgentConfig={enabled:" + mcpAgentEnabled + ",systemPrompt:'" + agentPrompt + "',maxRounds:" + agentMaxRounds + ",temperature:parseFloat('" + agentTemperature + "')||0.2,maxTokens:" + agentMaxTokens + ",mcpSdkBaseUrl:'" + mcpSdkBaseUrl + "',plannerEnabled:" + plannerEnabled + ",fileContextEnabled:" + fileContextEnabled + ",visionEnabled:" + visionEnabled + ",memoryEnabled:" + memoryEnabled + ",historyEnabled:" + historyEnabled + ",autoMemoryEnabled:" + autoMemoryEnabled + ",reasoningEffort:'" + agentReasoningEffort + "',plannerModel:'" + plannerModel + "',executorModel:'" + executorModel + "',visionModel:'" + visionModel + "',maxFileChars:" + agentMaxFileChars + ",memoryMaxItems:" + agentMemoryMaxItems + ",mcpServers:mcpServers}; window.SOViewerUIConfig={darkTheme:" + darkTheme + ",debugConsole:" + debugConsole + "};\n"
                + "  window.SOViewerLimits={saveMaxBytes:" + saveMaxBytes + ",preloadMaxBytes:" + preloadMaxBytes + ",proxyResponseMaxBytes:" + proxyResponseMaxBytes + ",sessionTtlMinutes:" + sessionTtlMinutes + "};\n})();";
    }

    // ============================================================
    // 模板
    // ============================================================

    public static String getTemplate() {
        String cached = cachedTemplateHtml;
        if (cached != null) return cached;
        String t = readAsset("template.html");
        if (t == null || t.isEmpty()) {
            t = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body>"
                    + "<div id=\"preview-container\"></div>"
                    + "<script>/* PLUGIN_GLOBALS_INJECTION */</script>"
                    + "<script>/* USER_SCRIPT_INJECTION_POINT */</script>"
                    + "</body></html>";
        }
        cachedTemplateHtml = t;
        return t;
    }

    private static String defaultAgentPrompt() {
        return "你是一个专业的文件分析助手，帮助用户理解当前预览的文件内容。";
    }

    // ============================================================
    // 状态摘要（原 buildColoredStatusSummary）
    // ============================================================

    public static CharSequence buildColoredStatusSummary(String label, String status, int color, String suffix) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (label != null && !label.isEmpty()) sb.append(label);
        int start = sb.length();
        sb.append(status == null ? "" : status);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (suffix != null && !suffix.isEmpty()) sb.append(suffix);
        return sb;
    }

    // ============================================================
    // 文件选择辅助
    // ============================================================

    /** 按扩展名自动选脚本；多个匹配时返回全部，交给调用方弹选择框 */
    public static List<JSONObject> pickScriptsFor(String fileName) {
        String ext = JsonUtils.getExtension(fileName);
        return matchScripts(ext);
    }

    public static String scriptDisplayName(JSONObject script) {
        return JsonUtils.optString(script, "name", "Unknown");
    }

    /** 供 Activity 使用：把外部 Uri 的文件名映射到预览 */
    public static Intent buildPreviewIntent(android.content.Context ctx, String fileName, File file) {
        return null; // 由 Activity 自行编排，占位保持扩展点
    }
}