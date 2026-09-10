async function render(fileName, fileExt, content) {
    const cfg = window.SOViewerAgentConfig || {};
    const api = window.SOViewerAPIConfig || {};
    const bytes = content || new Uint8Array();
    const ext = String(fileExt || "").toLowerCase();
    const textDecoder = new TextDecoder();
    let fileText = "";
    try { fileText = textDecoder.decode(bytes); } catch (e) { fileText = ""; }

    const root = document.createElement("div");
    root.className = "agent-root";
    root.innerHTML = `
        <style>
            .agent-root{height:100vh;display:grid;grid-template-columns:minmax(0,1fr) 320px;background:#f5f7fa;color:#1f2933;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;overflow:hidden}
            .agent-main{display:flex;flex-direction:column;min-width:0;border-right:1px solid #dfe6ee;background:#fff}
            .agent-head{display:flex;align-items:center;gap:8px;padding:11px 13px;border-bottom:1px solid #e4eaf1;background:#fbfcfe}
            .agent-title{font-size:16px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.agent-sub{font-size:12px;color:#667085;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .agent-spacer{flex:1}.agent-btn{height:33px;border:1px solid #ccd6e2;background:#fff;color:#243447;border-radius:6px;padding:0 10px;font-size:13px;cursor:pointer}.agent-btn.primary{background:#1565c0;color:#fff;border-color:#1565c0}.agent-btn:disabled{opacity:.55;cursor:not-allowed}
            .agent-chat{flex:1;overflow:auto;padding:14px;background:#f5f7fa}.agent-msg{max-width:960px;margin:0 0 12px;display:flex;gap:9px}.agent-role{width:58px;flex:none;font-size:12px;color:#667085;text-align:right;padding-top:8px}
            .agent-bubble{min-width:0;max-width:calc(100% - 67px);border:1px solid #dfe6ee;background:#fff;border-radius:8px;padding:10px 12px;line-height:1.55;font-size:14px;word-break:break-word}.agent-bubble pre{overflow:auto;background:#f3f5f8;border:1px solid #dfe4ec;border-radius:6px;padding:8px}.agent-bubble code{background:#eef2f7;border-radius:4px;padding:1px 4px}.agent-bubble p{margin:0 0 8px}.agent-bubble p:last-child{margin-bottom:0}
            .agent-msg.user .agent-bubble{background:#eaf3ff;border-color:#bed7f5}.agent-msg.plan .agent-bubble{background:#fff8e6;border-color:#e9c66d}.agent-msg.reason .agent-bubble{background:#f2f0ff;border-color:#cfc8fb}.agent-msg.tool .agent-bubble{background:#eefaf1;border-color:#b8dfc2;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12px;white-space:pre-wrap}.agent-msg.system .agent-bubble{background:#fff8e6;border-color:#f1d18a;color:#5f4300}
            .agent-compose{border-top:1px solid #e4eaf1;padding:10px;background:#fbfcfe}.agent-input-row{display:flex;gap:8px;align-items:flex-end}.agent-input{flex:1;min-height:58px;max-height:170px;resize:vertical;border:1px solid #ccd6e2;border-radius:7px;padding:9px 10px;font-size:14px;line-height:1.45;font-family:inherit;box-sizing:border-box}
            .agent-side{overflow:auto;background:#eef2f7;padding:11px}.agent-panel{border:1px solid #d8e0ea;background:#fff;border-radius:8px;margin-bottom:10px;overflow:hidden}.agent-panel h3{font-size:13px;margin:0;padding:10px 11px;border-bottom:1px solid #e6ebf1;background:#fbfcfd}.agent-kv{padding:8px 11px;font-size:12px;color:#4b5565;line-height:1.55;border-bottom:1px solid #f0f2f5}.agent-kv:last-child{border-bottom:0}
            .agent-good{color:#2e7d32;font-weight:650}.agent-warn{color:#d84315;font-weight:650}.agent-muted{color:#7b8492}.agent-tools,.agent-memory{max-height:210px;overflow:auto}.agent-tool,.agent-memory-item{padding:8px 11px;border-bottom:1px solid #f0f2f5;font-size:12px}.agent-tool:last-child,.agent-memory-item:last-child{border-bottom:0}.agent-tool-name{font-weight:650;color:#26384d}.agent-tool-desc,.agent-memory-item{color:#667085;line-height:1.4}.agent-log{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:11px;line-height:1.45;max-height:230px;overflow:auto;padding:9px 11px;white-space:pre-wrap;word-break:break-word;color:#445}
            @media(max-width:820px){.agent-root{grid-template-columns:1fr}.agent-side{display:none}.agent-title{font-size:15px}.agent-role{width:42px}.agent-bubble{max-width:calc(100% - 51px)}}
        </style>
        <section class="agent-main">
            <header class="agent-head">
                <div style="min-width:0"><div class="agent-title">MCP Agent 工作台</div><div class="agent-sub"></div></div>
                <div class="agent-spacer"></div>
                <button class="agent-btn" data-action="discover">工具</button>
                <button class="agent-btn" data-action="memory">清记忆</button>
                <button class="agent-btn" data-action="new">新对话</button>
            </header>
            <main class="agent-chat"></main>
            <footer class="agent-compose">
                <div class="agent-input-row">
                    <textarea class="agent-input" placeholder="输入任务。Agent 会先规划，再按需调用 MCP 工具、读取文件/图片上下文并连续对话..."></textarea>
                    <button class="agent-btn primary" data-action="send">发送</button>
                </div>
            </footer>
        </section>
        <aside class="agent-side">
            <div class="agent-panel"><h3>运行状态</h3><div class="agent-kv" data-status></div><div class="agent-kv" data-model></div><div class="agent-kv" data-policy></div><div class="agent-kv" data-file></div></div>
            <div class="agent-panel"><h3>MCP 工具</h3><div class="agent-tools"></div></div>
            <div class="agent-panel"><h3>长期记忆</h3><div class="agent-memory"></div></div>
            <div class="agent-panel"><h3>调用日志</h3><div class="agent-log"></div></div>
        </aside>
    `;

    const chatEl = root.querySelector(".agent-chat");
    const inputEl = root.querySelector(".agent-input");
    const sendBtn = root.querySelector('[data-action="send"]');
    const discoverBtn = root.querySelector('[data-action="discover"]');
    const newBtn = root.querySelector('[data-action="new"]');
    const memoryBtn = root.querySelector('[data-action="memory"]');
    const toolsEl = root.querySelector(".agent-tools");
    const memoryEl = root.querySelector(".agent-memory");
    const logEl = root.querySelector(".agent-log");
    const subEl = root.querySelector(".agent-sub");
    const state = {
        busy: false,
        messages: [],
        tools: [],
        toolMap: new Map(),
        clients: new Map(),
        sdk: null,
        markdown: null,
        memories: [],
        servers: Array.isArray(cfg.mcpServers) ? cfg.mcpServers.filter(s => s && s.enabled !== false) : [],
        imageDataUrl: null
    };

    const storagePrefix = "sopreviewer.agent.";
    const memoryKey = storagePrefix + "memory.v2";
    const historyKey = storagePrefix + "history.v2";
    const isImage = /^(png|jpg|jpeg|gif|webp|bmp)$/.test(ext);
    if (isImage && cfg.visionEnabled) state.imageDataUrl = makeDataUrl(bytes, ext);

    async function dynamicImport(url) {
        try {
            const importer = new Function("u", "return import(u)");
            return await importer(url);
        } catch (e) {
            throw new Error("当前 WebView 不支持动态模块加载: " + (e && e.message ? e.message : e));
        }
    }

    function setBusy(value) {
        state.busy = value;
        sendBtn.disabled = value;
        discoverBtn.disabled = value;
        inputEl.disabled = value;
    }
    function log(text) {
        const time = new Date().toLocaleTimeString();
        logEl.textContent += `[${time}] ${text}\n`;
        logEl.scrollTop = logEl.scrollHeight;
    }
    async function loadMarkdown() {
        if (state.markdown) return state.markdown;
        try {
            const [markedMod, purifyMod] = await Promise.all([
                dynamicImport("https://esm.sh/marked@14.1.3"),
                dynamicImport("https://esm.sh/dompurify@3.1.7")
            ]);
            state.markdown = { marked: markedMod.marked || markedMod.default, DOMPurify: purifyMod.default || purifyMod.DOMPurify };
        } catch (e) {
            log(`Markdown deps unavailable: ${e.message}`);
            state.markdown = { marked: null, DOMPurify: null };
        }
        return state.markdown;
    }
    function addMessage(role, text, cls, markdown) {
        const row = document.createElement("div");
        row.className = `agent-msg ${cls || role}`;
        const roleEl = document.createElement("div");
        roleEl.className = "agent-role";
        roleEl.textContent = role;
        const bubble = document.createElement("div");
        bubble.className = "agent-bubble";
        row.append(roleEl, bubble);
        chatEl.appendChild(row);
        setBubble(bubble, text || "", markdown);
        chatEl.scrollTop = chatEl.scrollHeight;
        return bubble;
    }
    async function setBubble(bubble, text, markdown) {
        if (!markdown) {
            bubble.textContent = text || "";
            return;
        }
        const md = await loadMarkdown();
        if (md.marked && md.DOMPurify) bubble.innerHTML = md.DOMPurify.sanitize(md.marked.parse(text || ""));
        else bubble.textContent = text || "";
    }
    function updateStatus() {
        root.querySelector("[data-status]").innerHTML = `Agent：<span class="${cfg.enabled ? "agent-good" : "agent-warn"}">${cfg.enabled ? "已启用" : "未启用"}</span> · 记忆 <span class="${cfg.memoryEnabled ? "agent-good" : "agent-muted"}">${cfg.memoryEnabled ? "开启" : "关闭"}</span>`;
        root.querySelector("[data-model]").innerHTML = `模型：<span class="${api.modelId ? "agent-good" : "agent-warn"}">${escapeHtml(api.modelId || "未配置")}</span> · 推理 ${escapeHtml(cfg.reasoningEffort || "medium")}`;
        root.querySelector("[data-policy]").innerHTML = `权限：<span class="${api.trustedMode ? "agent-good" : "agent-warn"}">${api.trustedMode ? "可信脚本" : "未授信"}</span> · 代理 <span class="${api.proxyEnabled ? "agent-good" : "agent-warn"}">${api.proxyEnabled ? "开启" : "关闭"}</span>`;
        root.querySelector("[data-file]").textContent = `文件：${fileName || "unknown"} · ${bytes.length} 字节 · ${state.imageDataUrl ? "图片上下文" : (cfg.fileContextEnabled ? "文本上下文" : "未注入")}`;
        subEl.textContent = `${state.servers.length} 个 MCP 服务，${state.tools.length} 个工具，${state.memories.length} 条记忆，最大 ${cfg.maxRounds || 6} 轮`;
    }
    function renderTools() {
        toolsEl.innerHTML = "";
        if (!state.tools.length) {
            const empty = document.createElement("div");
            empty.className = "agent-kv agent-muted";
            empty.textContent = "暂无工具。点击工具刷新，或检查 MCP 服务配置。";
            toolsEl.appendChild(empty);
            updateStatus();
            return;
        }
        state.tools.forEach(tool => {
            const item = document.createElement("div");
            item.className = "agent-tool";
            item.innerHTML = `<div class="agent-tool-name"></div><div class="agent-tool-desc"></div>`;
            item.querySelector(".agent-tool-name").textContent = tool.function.name;
            item.querySelector(".agent-tool-desc").textContent = tool.function.description || "无描述";
            toolsEl.appendChild(item);
        });
        updateStatus();
    }
    function renderMemory() {
        memoryEl.innerHTML = "";
        if (!state.memories.length) {
            const empty = document.createElement("div");
            empty.className = "agent-kv agent-muted";
            empty.textContent = "暂无长期记忆。连续对话后会自动提炼。";
            memoryEl.appendChild(empty);
            updateStatus();
            return;
        }
        state.memories.slice(-30).reverse().forEach(mem => {
            const item = document.createElement("div");
            item.className = "agent-memory-item";
            item.textContent = `${mem.type || "memory"} · ${mem.text || mem}`;
            memoryEl.appendChild(item);
        });
        updateStatus();
    }
    function loadMemory() {
        if (!cfg.memoryEnabled) return;
        try {
            const parsed = JSON.parse(localStorage.getItem(memoryKey) || "[]");
            state.memories = Array.isArray(parsed) ? parsed : [];
        } catch (e) { state.memories = []; }
    }
    function saveMemory() {
        if (!cfg.memoryEnabled) return;
        const max = Number(cfg.memoryMaxItems || 80);
        const kept = max > 0 ? state.memories.slice(-max) : state.memories;
        state.memories = kept;
        localStorage.setItem(memoryKey, JSON.stringify(kept));
        renderMemory();
    }
    function resetConversation() {
        state.messages = [{ role: "system", content: buildSystemPrompt() }];
        const fileCtx = buildFileContext();
        if (fileCtx) state.messages.push({ role: "system", content: fileCtx });
        chatEl.innerHTML = "";
        addMessage("system", "已创建新对话。Agent 会使用长期记忆、当前文件上下文和 MCP 工具完成任务。", "system");
        if (cfg.historyEnabled) localStorage.removeItem(historyKey);
    }
    function restoreHistory() {
        if (!cfg.historyEnabled) return false;
        try {
            const saved = JSON.parse(localStorage.getItem(historyKey) || "null");
            if (!saved || !Array.isArray(saved.messages) || !saved.messages.length) return false;
            state.messages = saved.messages;
            chatEl.innerHTML = "";
            addMessage("system", "已恢复上次会话。", "system");
            return true;
        } catch (e) {
            return false;
        }
    }
    function persistHistory() {
        if (!cfg.historyEnabled) return;
        localStorage.setItem(historyKey, JSON.stringify({ ts: Date.now(), messages: state.messages.slice(-60) }));
    }
    function buildSystemPrompt() {
        const memoryText = cfg.memoryEnabled && state.memories.length
            ? "\n\n长期记忆：\n" + state.memories.slice(-20).map((m, i) => `${i + 1}. [${m.type || "memory"}] ${m.text || m}`).join("\n")
            : "";
        return `${cfg.systemPrompt || "你是一个专业 Agent。"}\n\n界面约定：输出中用“计划 / 推理摘要 / 工具观察 / 最终回答”组织内容；不要泄露隐藏思维链，只给可审计摘要。${memoryText}`;
    }
    function buildFileContext() {
        if (!cfg.fileContextEnabled) return "";
        if (state.imageDataUrl) return `当前文件是图片：${fileName || "unknown"}，大小 ${bytes.length} 字节。用户问题需要图片内容时，可将图片输入发送给视觉模型。`;
        const limit = Math.max(0, Math.min(200000, Number(cfg.maxFileChars || 12000)));
        if (!limit || !fileText.trim()) return "";
        return `当前打开文件：${fileName || "unknown"}，扩展名 .${ext || "unknown"}，大小 ${bytes.length} 字节。\n\n文件文本片段：\n${fileText.slice(0, limit)}`;
    }

    function buildProxyUrl(url) {
        return api.proxyBase ? api.proxyBase + encodeURIComponent(url) : url;
    }
    function toolSafeName(serverName, toolName) {
        return (`${serverName}_${toolName}`).replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 64);
    }
    function normalizeBaseUrl(url) {
        return String(url || "https://esm.sh/@modelcontextprotocol/sdk@1.29.0").replace(/\/+$/, "");
    }
    async function loadMcpSdk() {
        if (state.sdk) return state.sdk;
        const base = normalizeBaseUrl(cfg.mcpSdkBaseUrl);
        const [clientMod, httpMod, sseMod] = await Promise.all([
            dynamicImport(`${base}/client/index.js`),
            dynamicImport(`${base}/client/streamableHttp.js`),
            dynamicImport(`${base}/client/sse.js`)
        ]);
        state.sdk = { Client: clientMod.Client, StreamableHTTPClientTransport: httpMod.StreamableHTTPClientTransport, SSEClientTransport: sseMod.SSEClientTransport };
        log(`MCP SDK loaded: ${base}`);
        return state.sdk;
    }
    async function connectServer(server) {
        if (state.clients.has(server.name)) return state.clients.get(server.name);
        const sdk = await loadMcpSdk();
        const requestInit = { headers: server.headers || {} };
        const client = new sdk.Client({ name: "SOPreviewer Agent", version: "1.0.0" }, { capabilities: {} });
        const type = String(server.type || "streamable-http").toLowerCase();
        const transport = type === "sse"
            ? new sdk.SSEClientTransport(new URL(buildProxyUrl(server.url)), { requestInit })
            : new sdk.StreamableHTTPClientTransport(new URL(buildProxyUrl(server.url)), { requestInit });
        await client.connect(transport);
        state.clients.set(server.name, client);
        log(`${server.name}: connected (${type})`);
        return client;
    }
    function addTool(server, tool, kind) {
        const name = toolSafeName(server.name || "mcp", tool.name || "tool");
        state.tools.push({ type: "function", function: { name, description: tool.description || `${server.name || "MCP"} tool ${tool.name || name}`, parameters: tool.inputSchema || tool.parameters || { type: "object", properties: {} } } });
        state.toolMap.set(name, { server, originalName: tool.name || name, kind, manual: tool });
    }
    async function discoverTools() {
        setBusy(true);
        state.tools = [];
        state.toolMap.clear();
        try {
            for (const server of state.servers) {
                if (server.type === "manual") {
                    (server.tools || []).forEach(tool => addTool(server, tool, "manual"));
                    log(`${server.name}: loaded ${(server.tools || []).length} manual tools`);
                    continue;
                }
                if (!server.url) {
                    log(`${server.name}: missing url`);
                    continue;
                }
                const client = await connectServer(server);
                const result = await client.listTools();
                const tools = Array.isArray(result.tools) ? result.tools : [];
                tools.forEach(tool => addTool(server, tool, "mcp"));
                log(`${server.name}: discovered ${tools.length} tools`);
            }
        } catch (e) {
            addMessage("system", `工具发现失败：${e.message}`, "system");
            log(`discover failed: ${e.stack || e.message}`);
        } finally {
            renderTools();
            setBusy(false);
        }
    }
    async function callTool(name, args) {
        const meta = state.toolMap.get(name);
        if (!meta) throw new Error(`unknown tool ${name}`);
        log(`call ${name} ${JSON.stringify(args || {})}`);
        if (meta.kind === "manual") return callManualTool(meta, args || {});
        const client = await connectServer(meta.server);
        const result = await client.callTool({ name: meta.originalName, arguments: args || {} });
        return stringifyToolResult(result);
    }
    async function callManualTool(meta, args) {
        const tool = meta.manual || {};
        if (!tool.url) return JSON.stringify({ error: "manual tool missing url" });
        const method = (tool.method || "POST").toUpperCase();
        const headers = Object.assign({ "Content-Type": "application/json" }, meta.server.headers || {}, tool.headers || {});
        const options = { method, headers };
        if (method !== "GET" && method !== "HEAD") options.body = tool.bodyTemplate ? applyTemplate(tool.bodyTemplate, args) : JSON.stringify(args || {});
        const res = await fetch(buildProxyUrl(tool.url), options);
        const text = await res.text();
        if (!res.ok) throw new Error(`${res.status} ${text.slice(0, 160)}`);
        return text.slice(0, 24000);
    }
    function applyTemplate(template, args) {
        return String(template).replace(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g, (_, key) => {
            const value = key.split(".").reduce((obj, k) => obj && obj[k], args);
            return value == null ? "" : String(value);
        });
    }
    function stringifyToolResult(result) {
        if (!result) return "";
        if (Array.isArray(result.content)) {
            return result.content.map(item => item && item.type === "text" ? item.text || "" : JSON.stringify(item)).join("\n").slice(0, 24000);
        }
        return JSON.stringify(result).slice(0, 24000);
    }

    function modelFor(kind) {
        if (kind === "planner") return cfg.plannerModel || api.modelId;
        if (kind === "vision") return cfg.visionModel || cfg.executorModel || api.modelId;
        return cfg.executorModel || api.modelId;
    }
    async function chatCompletion(messages, options) {
        const headers = { "Content-Type": "application/json" };
        if (api.apiKey) headers.Authorization = `Bearer ${api.apiKey}`;
        const body = {
            model: modelFor(options && options.kind),
            messages,
            temperature: Number(cfg.temperature || 0.2),
            max_tokens: Number(cfg.maxTokens || 2048)
        };
        if (options && options.tools && state.tools.length) body.tools = state.tools;
        if (cfg.reasoningEffort) body.reasoning_effort = cfg.reasoningEffort;
        let res = await fetch(buildProxyUrl(api.apiUrl), { method: "POST", headers, body: JSON.stringify(body) });
        let text = await res.text();
        if (!res.ok && body.reasoning_effort) {
            delete body.reasoning_effort;
            res = await fetch(buildProxyUrl(api.apiUrl), { method: "POST", headers, body: JSON.stringify(body) });
            text = await res.text();
        }
        if (!res.ok) throw new Error(`${res.status} ${text.slice(0, 260)}`);
        const data = JSON.parse(text);
        const msg = data.choices && data.choices[0] && data.choices[0].message;
        if (!msg) throw new Error("API 响应缺少 choices[0].message");
        return msg;
    }
    async function makePlan(userText) {
        if (!cfg.plannerEnabled) return "";
        const msgs = [
            { role: "system", content: "你是 Agent 规划器。输出简短计划，列出子任务、需要的工具、需要读取的文件或图片上下文、风险点。不要调用工具。" },
            { role: "user", content: `用户任务：${userText}\n\n可用工具：${state.tools.map(t => t.function.name + ": " + (t.function.description || "")).join("\n") || "暂无"}\n\n文件上下文：${buildFileContext().slice(0, 4000) || "无"}\n\n长期记忆：${state.memories.slice(-12).map(m => m.text || m).join("\n") || "无"}` }
        ];
        const msg = await chatCompletion(msgs, { kind: "planner", tools: false });
        return msg.content || "";
    }
    function userMessage(text) {
        if (state.imageDataUrl) {
            return { role: "user", content: [{ type: "text", text }, { type: "image_url", image_url: { url: state.imageDataUrl } }] };
        }
        return { role: "user", content: text };
    }
    async function extractMemory(userText, finalText) {
        if (!cfg.memoryEnabled || !cfg.autoMemoryEnabled) return;
        try {
            const msgs = [
                { role: "system", content: "从对话中提炼长期记忆。只返回 JSON 数组，每项 {\"type\":\"preference|project|fact|task|warning\",\"text\":\"...\"}。只保留未来有复用价值的信息，最多 5 条。" },
                { role: "user", content: `用户：${userText}\n\n助手最终回答：${finalText}` }
            ];
            const msg = await chatCompletion(msgs, { kind: "planner", tools: false });
            const arr = parseJsonArray(msg.content || "");
            if (arr.length) {
                arr.forEach(item => {
                    if (item && item.text) state.memories.push({ type: item.type || "memory", text: String(item.text).slice(0, 600), ts: Date.now() });
                });
                saveMemory();
                log(`memory extracted: ${arr.length}`);
            }
        } catch (e) {
            log(`memory extraction skipped: ${e.message}`);
        }
    }
    async function send() {
        const text = inputEl.value.trim();
        if (!text || state.busy) return;
        if (!cfg.enabled) return addMessage("system", "MCP Agent 未启用，请先到设置页开启。", "system");
        if (!api.apiUrl || !api.modelId) return addMessage("system", "API 端点或默认模型未配置。请打开 MCP Agent 配置填写端点、密钥和模型。", "system");
        inputEl.value = "";
        addMessage("user", text, "user");
        setBusy(true);
        let finalText = "";
        const answerBubble = addMessage("assistant", "准备任务...", "reason", true);
        try {
            if (!state.tools.length && state.servers.length) await discoverTools();
            const plan = await makePlan(text);
            if (plan) {
                addMessage("计划", plan, "plan", true);
                state.messages.push({ role: "system", content: `可见执行计划：\n${plan}` });
            }
            state.messages.push(userMessage(text));
            const maxRounds = Math.max(1, Math.min(20, Number(cfg.maxRounds || 6)));
            for (let round = 0; round < maxRounds; round++) {
                await setBubble(answerBubble, `执行第 ${round + 1}/${maxRounds} 轮...`, true);
                const msg = await chatCompletion(state.messages, { kind: state.imageDataUrl ? "vision" : "executor", tools: true });
                state.messages.push(msg);
                const calls = Array.isArray(msg.tool_calls) ? msg.tool_calls : [];
                if (!calls.length) {
                    finalText = msg.content || "(无文本回复)";
                    await setBubble(answerBubble, finalText, true);
                    break;
                }
                addMessage("推理摘要", `模型决定调用 ${calls.length} 个工具，以获取缺失证据或执行子任务。`, "reason", true);
                for (const call of calls) {
                    const fn = call.function || {};
                    let args = {};
                    try { args = fn.arguments ? JSON.parse(fn.arguments) : {}; } catch (e) { args = { raw: fn.arguments || "" }; }
                    let result;
                    try { result = await callTool(fn.name, args); }
                    catch (e) { result = `Tool error: ${e.message}`; }
                    addMessage("工具", `${fn.name}\n${result}`, "tool");
                    state.messages.push({ role: "tool", tool_call_id: call.id, name: fn.name, content: result });
                }
                if (round === maxRounds - 1) {
                    finalText = "已达到最大工具循环轮数。请提高轮数、缩小任务范围，或检查工具是否返回了足够信息。";
                    await setBubble(answerBubble, finalText, true);
                }
            }
            await extractMemory(text, finalText);
            persistHistory();
        } catch (e) {
            await setBubble(answerBubble, `请求失败：${e.message}`, true);
            log(e.stack || e.message);
        } finally {
            setBusy(false);
        }
    }

    function makeDataUrl(data, extension) {
        const mime = extension === "jpg" ? "image/jpeg" : `image/${extension || "png"}`;
        let binary = "";
        const chunk = 0x8000;
        for (let i = 0; i < data.length; i += chunk) binary += String.fromCharCode.apply(null, data.subarray(i, i + chunk));
        return `data:${mime};base64,${btoa(binary)}`;
    }
    function parseJsonArray(text) {
        const raw = String(text || "").trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
        const start = raw.indexOf("[");
        const end = raw.lastIndexOf("]");
        if (start >= 0 && end > start) {
            try { const arr = JSON.parse(raw.slice(start, end + 1)); return Array.isArray(arr) ? arr : []; } catch (e) {}
        }
        return [];
    }
    function escapeHtml(text) {
        return String(text).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c]));
    }

    sendBtn.addEventListener("click", send);
    discoverBtn.addEventListener("click", discoverTools);
    newBtn.addEventListener("click", resetConversation);
    memoryBtn.addEventListener("click", () => {
        localStorage.removeItem(memoryKey);
        state.memories = [];
        renderMemory();
        addMessage("system", "长期记忆已清空。", "system");
    });
    inputEl.addEventListener("keydown", e => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            send();
        }
    });

    loadMemory();
    if (!restoreHistory()) resetConversation();
    updateStatus();
    renderTools();
    renderMemory();
    loadMarkdown();
    if (cfg.enabled && state.servers.length) discoverTools();
    return root;
}
