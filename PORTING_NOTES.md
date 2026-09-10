# SOPreviewer → 独立 App 移植说明

本目录是 [SOPreviewer](https://github.com/bilieebiliee1-design/SOPreviewer)（MT 管理器插件）改造后的**独立 Android 应用**工程。

## 改造目标

| 项 | 原插件 | 独立 App |
|---|---|---|
| 入口 | MT 编辑器工具菜单 | Launcher 图标 + 打开方式 |
| 宿主 | MT 管理器进程 | 自有前台服务 |
| 设置页 | MT PluginPreference | 原生 PreferenceFragmentCompat |
| 能力 | 全部保留 | **全部保留**（含 AI / MCP / 代理） |
| 协议 | AGPL-3.0 | AGPL-3.0（衍生作品） |

## API 替换对照表

原 `Core.java` 共 2287 行，MT API 调用仅 8 类，全部收敛到兼容层：

| 原 MT API | 调用次数 | 替换实现 | 位置 |
|---|---|---|---|
| `context.getPreferences()` | 31 | `App.prefs()` → `getSharedPreferences` | `App.java` / `Prefs.java` |
| `context.showToast(msg)` | 13 | `Dialogs.toast()` → `Toast` | `Dialogs.java` |
| `context.getFilesDir()` | 5 | `Utils.filesDir()` → `Context.getFilesDir()` | `Utils.java` |
| `context.openBrowser(url)` | 3 | `Dialogs` + `Intent.ACTION_VIEW` | `SettingsFragment` |
| `context.log(tag, e)` | 2 | `Log.e()` | 直接替换 |
| `context.openPreference(cls)` | 1 | `startActivity(SettingsActivity)` | `MainActivity` |
| `context.getAssetsAsStream()` | 1 | `getAssets().open()` | `PreviewEngine.readAsset` |
| `context.openBuiltinBrowser(url, topBar)` | 1 | 新建 `PreviewActivity` + WebView | `PreviewActivity.java` |
| `pluginUI.buildDialog()` | — | `Dialogs.Builder` 链式封装 | `Dialogs.java` |
| `editor.getFileName/getFilePath` | — | `Intent` 取 `Uri` → 拷入私有目录 | `MainActivity` / `OpenWithActivity` |
| `bin.mt.json.JSONObject/JSONArray` | 全项目 | `org.json` + `JsonUtils` 包装 | `JsonUtils.java` |

## 模块拆分（对应作者 `system_rebuild_plan.txt` 阶段五）

| 原内部类 | 原行号 | 新文件 | 改动内容 |
|---|---|---|---|
| `HttpServer` | 751–872 | `HttpServer.java` | **仅换 context 类型，逻辑零改动** |
| `HttpRequest/HttpResponse` | 873–932 | `HttpServer.java`（内部类） | 纯 JDK，零改动 |
| `ProxyService` | 933–1088 | `ProxyService.java` | 换 preferences |
| `CacheManager` | 1089–1442 | `CacheManager.java` | 换 `getFilesDir` + JSON |
| `UISettings` | 1443–2158 | `SettingsFragment.java` + `preferences.xml` | **重写为原生设置页** |
| `Utils` | 2159–2268 | `Utils.java` / `JsonUtils.java` | JSON API 适配 |
| 核心流程 | 186–750 | `PreviewEngine.java` | 入口改造，业务逻辑保留 |

## 保留的原始设计

以下关键设计**原样保留**，未做改动：

1. **本地 HTTP Server 绕跨域限制** —— `127.0.0.1:34877~35000` 端口扫描，失败则随机分配
2. **`/raw/{token}` 流式返回** —— 64KB 分块读写，大文件不占内存，天然规避 OOM
3. **会话 TTL 机制** —— `ConcurrentHashMap` + 10 分钟过期，超时清理临时文件
4. **trusted 权限模型** —— API Key 仅在「全局可信 + 脚本可信」双条件下注入 JS
5. **代理白名单 + 响应体积限制** —— 防止脚本滥用网络
6. **50MB 大文件警告** —— 预览前拦截

## 新增能力（独立 App 必需）

| 新增项 | 说明 |
|---|---|
| `SOViewerApplication` | 全局 Context 初始化 + 脚本预置 + Server 预热 |
| `PreviewService` | 前台服务（dataSync），维持后台 socket 存活 |
| `MainActivity` | 主页：最近文件列表 + 打开文件 + 设置入口 |
| `PreviewActivity` | WebView 容器，替代原 `openBuiltinBrowser` |
| `OpenWithActivity` | 无界面入口，接收 `ACTION_VIEW` / `ACTION_SEND` |
| 最近文件记录 | 最多 30 条，自动去重 |

## 构建

### 方式一：GitHub Actions（推荐）

推送代码后自动触发 `.github/workflows/build.yml`，产物在 Actions 的 Artifacts 中：

- `SOViewer-debug-apk`
- `SOViewer-release-apk`

也可在仓库 Actions 页手动 `workflow_dispatch` 触发。

### 方式二：本地构建

```bash
# 需 Android SDK（compileSdk 34）+ JDK 17
./gradlew assembleDebug     # 输出 app/build/outputs/apk/debug/
./gradlew assembleRelease   # 输出 app/build/outputs/apk/release/
```

### 环境要求

| 项 | 版本 |
|---|---|
| AGP | 8.0.2 |
| Gradle | 8.0.2 |
| JDK | 11（源码兼容）/ 17（推荐构建） |
| compileSdk | 34 |
| minSdk | 26（Android 8.0） |
| targetSdk | 34 |

### 签名

`release` 构建当前复用 `debug.keystore`（仅用于自测分发）。
正式发布请替换 `app/build.gradle` 中的 `signingConfigs.release`。

## 编译校验

工程已通过离线 `javac` 全量语法校验（38 个 class，零错误）。
校验用 R 存根位于 `.verify/R.java`，已在 `.gitignore` 中排除，不参与真实构建。

## 注意事项

1. **assets 必须完整** —— `so_viewer.js`(332KB)、`md_viewer.js`、`pdf_viewer.js`、`sqlite_viewer.js`、`agent_viewer.js`、`template.html` 是预览能力的全部家当，缺一不可
2. **`usesCleartextTraffic`** —— 仅放行 `127.0.0.1`，见 `res/xml/network_security_config.xml`
3. **AGPL-3.0** —— 本项目为衍生作品，二次分发需同样开源
4. **首次启动** —— 需同意使用协议；内置脚本按 `CURRENT_SCRIPTS_VERSION = 30` 写入

## 目录结构

```
SOViewerApp/
├── .github/workflows/build.yml    # CI 构建
├── app/
│   ├── build.gradle
│   ├── libs/                      # 保留的第三方 jar（构建时走 Maven）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                # ← 原插件全部 JS 脚本 + 模板
│       ├── java/com/viewer/so/app/
│       │   ├── App.java           # 全局 Context
│       │   ├── Consts.java        # 常量表（对应原 Core 常量）
│       │   ├── JsonUtils.java     # JSON 适配（替代 bin.mt.json）
│       │   ├── Prefs.java         # 偏好封装
│       │   ├── Utils.java         # IO 工具
│       │   ├── Dialogs.java       # 对话框兼容层
│       │   ├── HttpServer.java    # 本地服务（原样移植）
│       │   ├── CacheManager.java  # 缓存与预加载
│       │   ├── ProxyService.java  # 脚本代理
│       │   ├── PreviewEngine.java # 预览引擎（核心）
│       │   ├── MainActivity.java
│       │   ├── PreviewActivity.java
│       │   ├── OpenWithActivity.java
│       │   ├── SettingsActivity.java
│       │   ├── SettingsFragment.java
│       │   ├── PreviewService.java
│       │   ├── RecentAdapter.java
│       │   └── FileImporter.java
│       └── res/
│           ├── layout/            # 主页 / 预览 / 设置 / 列表项
│           ├── values/            # 主题 / 字符串
│           └── xml/               # preferences / 网络安全配置
├── gradle/wrapper/
└── build.gradle
```

## 致谢

原作者 [bilieebiliee1-design](https://github.com/bilieebiliee1-design)，
原项目 [SOPreviewer](https://github.com/bilieebiliee1-design/SOPreviewer)（AGPL-3.0）。
