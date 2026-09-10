package com.viewer.so.app;

/**
 * 常量表。与原 Core.java 的常量定义保持一一对应，便于比对。
 */
public final class Consts {

    private Consts() {}

    public static final String PREFS_NAME = "so_viewer_prefs";

    // ---- 偏好键 ----
    public static final String PREF_KEY_SCRIPTS = "scripts";
    public static final String PREF_KEY_SCRIPTS_VERSION = "scripts_version";
    public static final String PREF_KEY_SCRIPT_SAVE_ENABLED = "script_save_enabled";
    public static final String PREF_KEY_SCRIPT_API_ENABLED = "script_api_enabled";
    public static final String PREF_KEY_SCRIPT_API_URL = "script_api_url";
    public static final String PREF_KEY_SCRIPT_API_MODEL = "script_api_model";
    public static final String PREF_KEY_SCRIPT_API_KEY = "script_api_key";
    public static final String PREF_KEY_SCRIPT_PROXY_ENABLED = "script_proxy_enabled";
    public static final String PREF_KEY_SCRIPT_TRUSTED_MODE = "script_trusted_mode";
    public static final String PREF_KEY_ACTIVE_TRUSTED_SCRIPT = "active_trusted_script";
    public static final String PREF_KEY_LARGE_FILE_WARNING_MB = "large_file_warning_mb";
    public static final String PREF_KEY_PROXY_ALLOWLIST = "proxy_allowlist";
    public static final String PREF_KEY_CACHE_MAX_MB = "cache_max_mb";
    public static final String PREF_KEY_BROWSER_TOP_BAR_ENABLED = "browser_top_bar_enabled";
    public static final String PREF_KEY_MCP_AGENT_ENABLED = "mcp_agent_enabled";
    public static final String PREF_KEY_MCP_SERVERS_JSON = "mcp_servers_json";
    public static final String PREF_KEY_MCP_SDK_BASE_URL = "mcp_sdk_base_url";
    public static final String PREF_KEY_AGENT_SYSTEM_PROMPT = "agent_system_prompt";
    public static final String PREF_KEY_AGENT_MAX_ROUNDS = "agent_max_rounds";
    public static final String PREF_KEY_AGENT_TEMPERATURE = "agent_temperature";
    public static final String PREF_KEY_AGENT_MAX_TOKENS = "agent_max_tokens";
    public static final String PREF_KEY_AGENT_PLANNER_ENABLED = "agent_planner_enabled";
    public static final String PREF_KEY_AGENT_FILE_CONTEXT_ENABLED = "agent_file_context_enabled";
    public static final String PREF_KEY_AGENT_VISION_ENABLED = "agent_vision_enabled";
    public static final String PREF_KEY_AGENT_REASONING_EFFORT = "agent_reasoning_effort";
    public static final String PREF_KEY_AGENT_PLANNER_MODEL = "agent_planner_model";
    public static final String PREF_KEY_AGENT_EXECUTOR_MODEL = "agent_executor_model";
    public static final String PREF_KEY_AGENT_VISION_MODEL = "agent_vision_model";
    public static final String PREF_KEY_AGENT_MAX_FILE_CHARS = "agent_max_file_chars";
    public static final String PREF_KEY_AGENT_MEMORY_ENABLED = "agent_memory_enabled";
    public static final String PREF_KEY_AGENT_HISTORY_ENABLED = "agent_history_enabled";
    public static final String PREF_KEY_AGENT_AUTO_MEMORY_ENABLED = "agent_auto_memory_enabled";
    public static final String PREF_KEY_AGENT_MEMORY_MAX_ITEMS = "agent_memory_max_items";
    public static final String PREF_KEY_DARK_THEME = "dark_theme";
    public static final String PREF_KEY_DEBUG_CONSOLE = "debug_console";
    public static final String PREF_KEY_SHOW_ADVANCED_SETTINGS = "show_advanced_settings";
    public static final String PREF_KEY_SERVER_PORT_HINT = "server_port_hint";
    public static final String PREF_KEY_AUTO_CLEANUP_SESSIONS = "auto_cleanup_sessions";
    public static final String PREF_KEY_SESSION_TTL_MINUTES = "session_ttl_minutes";
    public static final String PREF_KEY_SAVE_MAX_MB = "save_max_mb";
    public static final String PREF_KEY_PRELOAD_MAX_MB = "preload_max_mb";
    public static final String PREF_KEY_PROXY_RESPONSE_MAX_MB = "proxy_response_max_mb";
    public static final String PREF_KEY_COMPAT_MODE = "compat_mode";
    public static final String PREF_KEY_EULA_AGREED = "eula_agreed";
    public static final String PREF_KEY_RECENT_FILES = "recent_files";

    // ---- 内置脚本 ID ----
    public static final String BUILTIN_ID_HTML = "builtin_html";
    public static final String BUILTIN_ID_TEXT = "builtin_text";
    public static final String BUILTIN_ID_SO = "builtin_so";
    public static final String BUILTIN_ID_MD = "builtin_md";
    public static final String BUILTIN_ID_SQLITE = "builtin_sqlite";
    public static final String BUILTIN_ID_PDF = "builtin_pdf";
    public static final String BUILTIN_ID_JSON = "builtin_json";
    public static final String BUILTIN_ID_XML = "builtin_xml";
    public static final String BUILTIN_ID_CSV = "builtin_csv";
    public static final String BUILTIN_ID_IMAGE = "builtin_image";
    public static final String BUILTIN_ID_AGENT = "builtin_agent";

    public static final int CURRENT_SCRIPTS_VERSION = 30;

    // ---- 服务器 ----
    public static final String LOCALHOST = "127.0.0.1";
    public static final int SERVER_PORT_PRIMARY = 34876;
    public static final int SERVER_PORT_SECONDARY = 35000;
    public static final int SERVER_PORT_SCAN_START = 34877;
    public static final int MAX_UPLOAD_BODY_SIZE = 512 * 1024 * 1024;

    // ---- 限额默认值 ----
    public static final int DEFAULT_LARGE_FILE_WARNING_MB = 50;
    public static final int DEFAULT_CACHE_MAX_MB = 100;
    public static final int DEFAULT_SAVE_MAX_MB = 80;
    public static final int DEFAULT_PRELOAD_MAX_MB = 64;
    public static final int DEFAULT_PROXY_RESPONSE_MAX_MB = 32;
    public static final int DEFAULT_SESSION_TTL_MINUTES = 10;

    public static final String CORS_EXPOSE_HEADERS =
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Expose-Headers: X-Preload-Candidate-Count, X-Preload-Selected-Url, " +
            "X-Preload-Selected-Latency, X-Preload-Cache-Status, X-Preload-Download-Status, " +
            "X-Preload-Probe-Results, X-Preload-Fail-Reason\r\n";

    public static final String BABEL_PRIMARY_URL =
            "https://s4.zstatic.net/ajax/libs/babel-standalone/7.23.10/babel.min.js";
    public static final String BABEL_FALLBACK_URL =
            "https://unpkg.com/@babel/standalone@7.23.10/babel.min.js";
    public static final String BABEL_RESOURCE_NAME = "babel.min.js";
}