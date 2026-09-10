async function render(e,t,a){let d=document.createElement("div");d.style.cssText='width: 100%; height: 100vh; display: flex; flex-direction: column; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #f5f7f9; color: #333; margin: 0; padding: 0; overflow: hidden;';let o=document.createElement("style");o.innerHTML=`
        * { box-sizing: border-box; }
        body { margin: 0; padding: 0; overflow: hidden; }
        .db-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 20px; background-color: #2c3e50; color: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); z-index: 10; flex-shrink: 0; }
        .db-header h2 { margin: 0; font-size: 18px; font-weight: 500; display: flex; align-items: center; gap: 10px; }
        .db-header .badge { background-color: #34495e; padding: 4px 8px; border-radius: 4px; font-size: 12px; }
        
        .db-main { display: flex; flex: 1; overflow: hidden; position: relative; }
        
        /* Sidebar */
        .db-sidebar { width: 250px; background-color: #fff; border-right: 1px solid #e0e0e0; display: flex; flex-direction: column; overflow: hidden; flex-shrink: 0; transition: width 0.3s; }
        .db-sidebar-header { padding: 15px; font-weight: bold; border-bottom: 1px solid #eee; background-color: #fafafa; color: #555; display: flex; justify-content: space-between; align-items: center; }
        .db-sidebar-group { font-size: 11px; text-transform: uppercase; color: #888; padding: 10px 15px 5px; font-weight: bold; }
        .db-table-list { flex: 1; overflow-y: auto; padding: 0 0 10px 0; margin: 0; list-style: none; }
        .db-table-item { padding: 8px 20px 8px 30px; cursor: pointer; display: flex; align-items: center; gap: 10px; transition: background-color 0.2s; color: #444; font-size: 13px; }
        .db-table-item:hover { background-color: #f0f4f8; }
        .db-table-item.active { background-color: #e3f2fd; color: #1976d2; border-right: 3px solid #1976d2; font-weight: 500; }
        .db-table-icon { font-size: 14px; opacity: 0.7; }
        
        /* Content Area */
        .db-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; background-color: #f5f7f9; }
        .db-tabs { display: flex; background-color: #fff; border-bottom: 1px solid #e0e0e0; padding: 0 10px; flex-shrink: 0; }
        .db-tab { padding: 12px 20px; cursor: pointer; color: #666; border-bottom: 2px solid transparent; transition: all 0.2s; font-weight: 500; }
        .db-tab:hover { color: #1976d2; }
        .db-tab.active { color: #1976d2; border-bottom-color: #1976d2; }
        
        .db-tab-content { flex: 1; overflow: hidden; display: none; padding: 15px; }
        .db-tab-content.active { display: flex; flex-direction: column; }
        
        /* Data Table */
        .db-data-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-shrink: 0; flex-wrap: wrap; gap: 10px; }
        .db-search-box { padding: 6px 12px; border: 1px solid #ccc; border-radius: 4px; width: 250px; font-size: 13px; }
        .db-btn { padding: 6px 12px; border: none; border-radius: 4px; background-color: #1976d2; color: white; cursor: pointer; font-size: 13px; transition: background-color 0.2s; display: inline-flex; align-items: center; gap: 5px; }
        .db-btn:hover { background-color: #1565c0; }
        .db-btn-secondary { background-color: #f0f0f0; color: #333; border: 1px solid #ccc; }
        .db-btn-secondary:hover { background-color: #e0e0e0; }
        
        .db-table-container { flex: 1; overflow: auto; background: white; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); border: 1px solid #e0e0e0; }
        .db-table { width: 100%; border-collapse: collapse; font-size: 13px; white-space: nowrap; }
        .db-table th, .db-table td { padding: 8px 12px; border: 1px solid #eee; text-align: left; max-width: 300px; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
        .db-table th { background-color: #fafafa; font-weight: 600; color: #555; position: sticky; top: 0; z-index: 1; box-shadow: 0 1px 0 #eee; cursor: default; }
        .db-table tr:nth-child(even) { background-color: #fafbfc; }
        .db-table tr:hover { background-color: #f0f4f8; }
        
        /* Data Types Colors */
        .val-null { color: #aaa; font-style: italic; }
        .val-blob { color: #e67e22; font-style: italic; font-weight: bold; }
        .val-number { color: #c0392b; font-weight: bold; }
        .val-string { color: #2980b9; }
        
        /* Pagination */
        .db-pagination { display: flex; justify-content: space-between; align-items: center; padding: 10px 0 0 0; flex-shrink: 0; font-size: 13px; color: #666; }
        .db-page-controls { display: flex; gap: 5px; align-items: center; }
        .db-page-btn { padding: 4px 8px; border: 1px solid #ccc; background: white; border-radius: 3px; cursor: pointer; }
        .db-page-btn:disabled { opacity: 0.5; cursor: not-allowed; }
        .db-page-btn:not(:disabled):hover { background: #f0f0f0; }
        
        /* SQL Editor */
        .db-sql-editor-container { display: flex; flex-direction: column; height: 100%; gap: 15px; }
        .db-sql-input-area { height: 150px; display: flex; flex-direction: column; gap: 10px; flex-shrink: 0; }
        .db-sql-textarea { flex: 1; padding: 10px; border: 1px solid #ccc; border-radius: 4px; font-family: monospace; font-size: 14px; resize: none; line-height: 1.4; }
        .db-sql-actions { display: flex; justify-content: space-between; align-items: center; }
        .db-sql-results { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: white; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); border: 1px solid #e0e0e0; }
        
        /* Database Info Tab */
        .db-info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 15px; margin-bottom: 20px; }
        .db-info-card { background: white; padding: 15px; border-radius: 4px; border: 1px solid #e0e0e0; text-align: center; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
        .db-info-card .num { font-size: 24px; font-weight: bold; color: #1976d2; margin-bottom: 5px; }
        .db-info-card .lbl { font-size: 12px; color: #666; text-transform: uppercase; }
        .db-ddl-code { background: #f8f9fa; padding: 15px; border-radius: 4px; border: 1px solid #eee; font-family: monospace; font-size: 13px; white-space: pre-wrap; word-break: break-all; margin-top: 15px; max-height: 200px; overflow-y: auto;}
        
        /* Error/Loading state */
        .db-message { display: flex; flex-direction: column; justify-content: center; align-items: center; height: 100%; color: #666; gap: 10px; }
        .db-error { color: #d32f2f; padding: 15px; background: #ffebee; border-radius: 4px; margin: 10px; border: 1px solid #ffcdd2; word-break: break-all; }
        
        /* Modal for full text view */
        .db-modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 9999; display: none; justify-content: center; align-items: center; opacity: 0; transition: opacity 0.2s; }
        .db-modal-overlay.show { display: flex; opacity: 1; }
        .db-modal { background: #fff; width: 80%; max-width: 600px; max-height: 80vh; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); display: flex; flex-direction: column; }
        .db-modal-header { padding: 15px 20px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; font-weight: bold; }
        .db-modal-close { cursor: pointer; font-size: 20px; color: #888; border: none; background: transparent; }
        .db-modal-close:hover { color: #333; }
        .db-modal-body { padding: 20px; overflow-y: auto; font-family: monospace; font-size: 14px; white-space: pre-wrap; word-break: break-all; line-height: 1.5; color: #333; }
        
        /* Toast Notification */
        .db-toast { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: rgba(50, 50, 50, 0.9); color: white; padding: 10px 20px; border-radius: 20px; font-size: 13px; z-index: 10000; display: none; opacity: 0; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.2); pointer-events: none;}
        .db-toast.show { display: block; opacity: 1; }

        /* Responsive */
        @media (max-width: 768px) {
            .db-sidebar { width: 150px; }
            .db-header h2 { font-size: 16px; }
            .db-tab { padding: 10px 15px; font-size: 14px; }
            .db-search-box { width: 150px; }
            .db-data-toolbar { flex-direction: column; align-items: stretch; }
            .db-data-toolbar > div { display: flex; gap: 10px; }
            .db-data-toolbar > div > button { flex: 1; justify-content: center; }
        }
        @media (max-width: 480px) {
            .db-sidebar { position: absolute; left: -200px; z-index: 20; height: calc(100% - 45px); width: 200px; box-shadow: 2px 0 5px rgba(0,0,0,0.1); }
            .db-sidebar.open { left: 0; }
            .db-menu-toggle { display: block !important; cursor: pointer; padding: 5px; }
            .db-main { position: relative; }
            .db-header { padding: 10px; }
            .db-tabs { overflow-x: auto; white-space: nowrap; }
        }
        .db-menu-toggle { display: none; }
        
        /* Scrollbar */
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-track { background: #f1f1f1; }
        ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: #a8a8a8; }
        
        /* Dark Mode */
        @media (prefers-color-scheme: dark) {
            .db-header { background-color: #1a1a1a; }
            .db-sidebar { background-color: #2d2d2d; border-color: #404040; color: #e0e0e0; }
            .db-sidebar-header { background-color: #252525; color: #ccc; border-color: #404040; }
            .db-table-item { color: #ccc; }
            .db-table-item:hover { background-color: #3d3d3d; }
            .db-table-item.active { background-color: #1e3a5f; color: #64b5f6; border-color: #64b5f6; }
            .db-content { background-color: #1e1e1e; }
            .db-tabs { background-color: #2d2d2d; border-color: #404040; }
            .db-tab { color: #aaa; }
            .db-tab:hover { color: #64b5f6; }
            .db-tab.active { color: #64b5f6; border-color: #64b5f6; }
            .db-table-container { background: #2d2d2d; border-color: #404040; }
            .db-table th { background-color: #333; color: #ddd; box-shadow: 0 1px 0 #555; border-color: #555; }
            .db-table td { border-color: #444; color: #ccc; }
            .db-table tr:nth-child(even) { background-color: #2a2a2a; }
            .db-table tr:hover { background-color: #383838; }
            .db-btn-secondary { background-color: #444; color: #ccc; border-color: #555; }
            .db-btn-secondary:hover { background-color: #555; }
            .db-search-box { background-color: #333; color: #fff; border-color: #555; }
            .db-sql-textarea { background-color: #333; color: #fff; border-color: #555; }
            .db-sql-results { background: #2d2d2d; border-color: #404040; }
            .db-info-card { background: #2d2d2d; border-color: #404040; box-shadow: none; }
            .db-ddl-code { background: #252525; border-color: #404040; color: #ccc; }
            .db-modal { background: #2d2d2d; }
            .db-modal-header { border-bottom-color: #404040; color: #eee; }
            .db-modal-body { color: #eee; }
            .val-null { color: #888; }
            .val-blob { color: #e67e22; }
            .val-number { color: #e74c3c; }
            .val-string { color: #3498db; }
            ::-webkit-scrollbar-track { background: #2d2d2d; }
            ::-webkit-scrollbar-thumb { background: #555; }
        }
    `,document.head.appendChild(o),d.innerHTML=`
        <div class="db-header">
            <h2>
                <span class="db-menu-toggle">☰</span>
                <span style="font-size: 18px;">🗄️</span> 数据库预览器
            </h2>
            <div class="badge">${e}</div>
        </div>
        <div class="db-main">
            <div class="db-message" id="db-loading">
                <div style="font-size: 32px; animation: spin 2s linear infinite;">⏳</div>
                <div style="margin-top: 15px;">正在加载数据库引擎...</div>
                <style>@keyframes spin { 100% { transform: rotate(360deg); } }</style>
            </div>
        </div>
        
        <!-- UI Components injected globally -->
        <div class="db-modal-overlay" id="db-global-modal">
            <div class="db-modal" onclick="event.stopPropagation()">
                <div class="db-modal-header">
                    <span id="db-modal-title">单元格详情</span>
                    <button class="db-modal-close" id="db-modal-close">&times;</button>
                </div>
                <div class="db-modal-body" id="db-modal-content"></div>
            </div>
        </div>
        <div class="db-toast" id="db-global-toast">操作成功</div>
    `;try{window.initSqlJs||await new Promise((e,t)=>{let a=document.createElement("script");a.src="https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.8.0/sql-wasm.js",a.onload=e,a.onerror=()=>t(Error("加载 sql.js 失败")),document.head.appendChild(a)});let l=await window.initSqlJs({locateFile:e=>"https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.8.0/sql-wasm.wasm"}),r=new l.Database(a),i=e=>{let t=r.exec(`SELECT name FROM sqlite_master WHERE type='${e}' AND name NOT LIKE 'sqlite_%' ORDER BY name;`);return t.length>0?t[0].values.map(e=>e[0]):[]},n=i("table"),s=i("view"),c=i("index"),b=i("trigger"),p=0,g=0;try{p=r.exec("PRAGMA page_count;")[0].values[0][0],g=r.exec("PRAGMA page_size;")[0].values[0][0]}catch(u){}let $=(p*g/1024).toFixed(2),x={activeTable:n.length>0?n[0]:s.length>0?s[0]:null,activeType:n.length>0?"table":s.length>0?"view":null,activeTab:"info",currentPage:1,pageSize:50,searchTerm:"",sqlQuery:"SELECT * FROM sqlite_master;"},v=(e,t,a)=>{if(0===e.length)return"";let d=`<div class="db-sidebar-group">${"table"===a?"数据表 Tables":"视图 Views"} (${e.length})</div>`;return d+=e.map(e=>`<li class="db-table-item ${e===x.activeTable?"active":""}" data-table="${e}" data-type="${a}"><span class="db-table-icon">${t}</span> ${e}</li>`).join("")},f=`
            <div class="db-sidebar" id="db-sidebar">
                <div class="db-sidebar-header">
                    数据库对象
                </div>
                <ul class="db-table-list" id="db-table-list">
                    ${v(n,"\uD83D\uDCC4","table")}
                    ${v(s,"\uD83D\uDC41️","view")}
                </ul>
            </div>
            <div class="db-content">
                <div class="db-tabs">
                    <div class="db-tab active" data-tab="info">📊 数据库概览</div>
                    <div class="db-tab" data-tab="data" id="tab-btn-data">📝 数据浏览</div>
                    <div class="db-tab" data-tab="structure" id="tab-btn-struct">⚙️ 表/视图结构</div>
                    <div class="db-tab" data-tab="sql">🔍 SQL 查询</div>
                </div>
                
                <!-- Info Tab (New Feature) -->
                <div class="db-tab-content active" id="tab-info" style="overflow-y: auto;">
                    <h3 style="margin-top: 0; color: #444;">数据库信息</h3>
                    <div class="db-info-grid">
                        <div class="db-info-card"><div class="num">${$}</div><div class="lbl">大小 (KB)</div></div>
                        <div class="db-info-card"><div class="num">${n.length}</div><div class="lbl">表 (Tables)</div></div>
                        <div class="db-info-card"><div class="num">${s.length}</div><div class="lbl">视图 (Views)</div></div>
                        <div class="db-info-card"><div class="num">${c.length}</div><div class="lbl">索引 (Indexes)</div></div>
                        <div class="db-info-card"><div class="num">${b.length}</div><div class="lbl">触发器 (Triggers)</div></div>
                    </div>
                    <h3 style="color: #444; border-bottom: 1px solid #eee; padding-bottom: 10px;">快速指引</h3>
                    <p style="font-size: 14px; color: #555; line-height: 1.6;">
                        • <b>数据省略：</b> 受限于屏幕宽度，表格内的长文本可能会被 <code>...</code> 省略。<b>点击任意单元格</b>即可在弹窗中查看完整内容。<br>
                        • <b>复制与导出：</b> WebView 环境受限无法直接唤起下载文件，点击“导出 CSV”时，系统会将数据转化为 Base64 文本并自动复制到您的剪贴板，您可以在外部解码恢复文件。<br>
                        • <b>侧边栏切换：</b> 点击左侧的表或视图可以快速切换分析目标。在移动设备上可以点击左上角 ☰ 图标展开侧边栏。
                    </p>
                </div>

                <!-- Data Tab -->
                <div class="db-tab-content" id="tab-data">
                    <div class="db-data-toolbar">
                        <input type="text" class="db-search-box" id="db-search" placeholder="在当前表中搜索...">
                        <div>
                            <button class="db-btn db-btn-secondary" id="db-refresh" title="刷新数据">🔄 刷新</button>
                            <button class="db-btn" id="db-export-csv" title="复制当前结果的 Base64 CSV">📥 导出 Base64 CSV</button>
                        </div>
                    </div>
                    <div class="db-table-container" id="db-data-container"></div>
                    <div class="db-pagination">
                        <span id="db-page-info">显示 0 - 0 条，共 0 条</span>
                        <div class="db-page-controls">
                            <button class="db-page-btn" id="db-page-prev">上一页</button>
                            <span id="db-page-current" style="margin: 0 5px;">1</span>
                            <button class="db-page-btn" id="db-page-next">下一页</button>
                        </div>
                    </div>
                </div>
                
                <!-- Structure Tab -->
                <div class="db-tab-content" id="tab-structure" style="overflow-y: auto;">
                    <h4 style="margin: 0 0 10px 0;">列信息 (PRAGMA)</h4>
                    <div class="db-table-container" id="db-structure-container" style="flex: none; max-height: 50%;"></div>
                    <h4 style="margin: 15px 0 5px 0;">DDL (Create Statement)</h4>
                    <div class="db-ddl-code" id="db-ddl-container"></div>
                </div>
                
                <!-- SQL Tab -->
                <div class="db-tab-content" id="tab-sql">
                    <div class="db-sql-editor-container">
                        <div class="db-sql-input-area">
                            <textarea class="db-sql-textarea" id="db-sql-input" placeholder="输入 SQL 语句，例如：SELECT * FROM users;"></textarea>
                            <div class="db-sql-actions">
                                <span style="font-size: 12px; color: #888;">提示: 支持多条 SQL 语句，使用分号分隔</span>
                                <button class="db-btn" id="db-sql-run">▶ 执行查询</button>
                            </div>
                        </div>
                        <div class="db-sql-results" id="db-sql-results">
                            <div class="db-message">输入 SQL 并点击执行查询</div>
                        </div>
                    </div>
                </div>
            </div>
        `,h=d.querySelector(".db-main");if(h.innerHTML=n.length>0||s.length>0?f:`<div class="db-message"><h3>⚠️ 数据库为空或无法识别</h3><p>未找到任何表或视图结构。</p></div>`,0===n.length&&0===s.length)return d;let m={sidebar:d.querySelector("#db-sidebar"),menuToggle:d.querySelector(".db-menu-toggle"),tableList:d.querySelector("#db-table-list"),tabs:d.querySelectorAll(".db-tab"),tabContents:d.querySelectorAll(".db-tab-content"),dataContainer:d.querySelector("#db-data-container"),structureContainer:d.querySelector("#db-structure-container"),ddlContainer:d.querySelector("#db-ddl-container"),searchInput:d.querySelector("#db-search"),pagePrev:d.querySelector("#db-page-prev"),pageNext:d.querySelector("#db-page-next"),pageCurrent:d.querySelector("#db-page-current"),pageInfo:d.querySelector("#db-page-info"),refreshBtn:d.querySelector("#db-refresh"),exportCsvBtn:d.querySelector("#db-export-csv"),sqlInput:d.querySelector("#db-sql-input"),sqlRunBtn:d.querySelector("#db-sql-run"),sqlResults:d.querySelector("#db-sql-results"),modalOverlay:document.getElementById("db-global-modal"),modalContent:document.getElementById("db-modal-content"),modalTitle:document.getElementById("db-modal-title"),modalClose:document.getElementById("db-modal-close"),toast:document.getElementById("db-global-toast")};m.modalOverlay=d.querySelector("#db-global-modal"),m.modalContent=d.querySelector("#db-modal-content"),m.modalTitle=d.querySelector("#db-modal-title"),m.modalClose=d.querySelector("#db-modal-close"),m.toast=d.querySelector("#db-global-toast");let y=e=>null==e?"":String(e).replace(/&/g,"&amp;").replace(/"/g,"&quot;").replace(/'/g,"&#39;").replace(/</g,"&lt;").replace(/>/g,"&gt;"),_=(e,t)=>{m.modalTitle.textContent=e||"详情",m.modalContent.textContent=t,m.modalOverlay.classList.add("show")},w=()=>{m.modalOverlay.classList.remove("show")},k=(e,t=3e3)=>{m.toast.textContent=e,m.toast.classList.add("show"),setTimeout(()=>{m.toast.classList.remove("show")},t)};m.modalClose.addEventListener("click",w),m.modalOverlay.addEventListener("click",w);let q=e=>{if(null==e)return'<span class="val-null">NULL</span>';if(e instanceof Uint8Array)return`<span class="val-blob">BLOB (${e.length} bytes)</span>`;if("number"==typeof e)return`<span class="val-number">${e}</span>`;let t=String(e).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#039;");return`<span class="val-string">${t}</span>`},T=e=>null==e?"NULL":e instanceof Uint8Array?`[Binary BLOB Data, Length: ${e.length} bytes]`:String(e),L=(e,t,a)=>{if(!e||!t||0===t.length){a.innerHTML='<div class="db-message">没有数据</div>';return}let d='<table class="db-table"><thead><tr>';e.forEach(e=>d+=`<th>${y(e)}</th>`),d+="</tr></thead><tbody>",t.forEach((t,a)=>{d+="<tr>",t.forEach((t,a)=>{let o=T(t);d+=`<td data-raw="${y(o)}" data-col="${y(e[a])}">${q(t)}</td>`}),d+="</tr>"}),d+="</tbody></table>",a.innerHTML=d},S=e=>{e.addEventListener("click",e=>{let t=e.target.closest("td");if(!t)return;let a=t.getAttribute("data-raw"),d=t.getAttribute("data-col")||"单元格内容";a&&_(`列: ${d}`,a)})};S(m.dataContainer),S(m.structureContainer),S(m.sqlResults);let E=()=>{if(x.activeTable)try{let e=`SELECT COUNT(*) FROM "${x.activeTable}"`,t=`SELECT * FROM "${x.activeTable}"`,a=[];if(x.searchTerm){let d=r.exec(`PRAGMA table_info("${x.activeTable}")`);if(d.length>0){let o=d[0].values.map(e=>e[1]),l=o.map(e=>`CAST("${e}" AS TEXT) LIKE ?`).join(" OR "),i=` WHERE ${l}`;e+=i,t+=i;for(let n=0;n<o.length;n++)a.push(`%${x.searchTerm}%`)}}let s=r.exec(e,a),c=s[0].values[0][0],b=Math.ceil(c/x.pageSize)||1;x.currentPage>b&&(x.currentPage=b),x.currentPage<1&&(x.currentPage=1),t+=` LIMIT ${x.pageSize} OFFSET ${(x.currentPage-1)*x.pageSize}`;let p=r.exec(t,a);p.length>0?L(p[0].columns,p[0].values,m.dataContainer):L(null,null,m.dataContainer),m.pageCurrent.textContent=x.currentPage;let g=0===c?0:(x.currentPage-1)*x.pageSize+1,u=Math.min(x.currentPage*x.pageSize,c);m.pageInfo.textContent=`显示 ${g} - ${u} 条，共 ${c} 条`,m.pagePrev.disabled=x.currentPage<=1,m.pageNext.disabled=x.currentPage>=b}catch($){m.dataContainer.innerHTML=`<div class="db-error">加载数据失败: ${y($.message)}</div>`}},C=()=>{if(x.activeTable)try{let e=r.exec(`PRAGMA table_info("${x.activeTable}")`);e.length>0?L(e[0].columns,e[0].values,m.structureContainer):m.structureContainer.innerHTML=`<div class="db-message">未获取到结构信息</div>`;let t=r.exec(`SELECT sql FROM sqlite_master WHERE name = '${x.activeTable}'`);t.length>0&&t[0].values[0][0]?m.ddlContainer.textContent=t[0].values[0][0]:m.ddlContainer.textContent="-- 无法获取 DDL 语句 (可能是内部系统表) --"}catch(a){m.structureContainer.innerHTML=`<div class="db-error">加载结构失败: ${y(a.message)}</div>`,m.ddlContainer.textContent="Error: "+a.message}},z=e=>{let t=document.createElement("textarea");t.value=e,t.style.position="fixed",t.style.left="-999999px",t.style.top="-999999px",document.body.appendChild(t),t.focus(),t.select();try{document.execCommand("copy"),k("已成功复制 Base64 CSV 数据至剪贴板！\n请在外部浏览器或工具中解码。")}catch(a){k("剪贴板访问失败，请检查浏览器权限！"),console.error("Copy fallback failed",a)}document.body.removeChild(t)},R=(e,t)=>{if(!e||!t||0===t.length){k("没有数据可导出");return}let a="";a+=e.map(e=>`"${String(e).replace(/"/g,'""')}"`).join(",")+"\r\n",t.forEach(e=>{a+=e.map(e=>null==e?"":e instanceof Uint8Array?'"[BLOB DATA]"':`"${String(e).replace(/"/g,'""')}"`).join(",")+"\r\n"});try{let d="\uFEFF"+a,o=btoa(unescape(encodeURIComponent(d))),l=`data:text/csv;base64,${o}`;navigator.clipboard&&navigator.clipboard.writeText?navigator.clipboard.writeText(l).then(()=>{k("✅ 已生成并复制 Base64 CSV 数据至剪贴板！")}).catch(e=>{z(l)}):z(l)}catch(r){k("生成 Base64 时发生错误: "+r.message)}};m.tableList.addEventListener("click",e=>{let t=e.target.closest(".db-table-item");t&&(m.tableList.querySelectorAll(".db-table-item").forEach(e=>e.classList.remove("active")),t.classList.add("active"),x.activeTable=t.dataset.table,x.activeType=t.dataset.type,x.currentPage=1,x.searchTerm="",m.searchInput.value="",E(),C(),"info"===x.activeTab&&d.querySelector("#tab-btn-data").click(),d.querySelector("#tab-btn-data").textContent="view"===x.activeType?"\uD83D\uDCDD 视图数据":"\uD83D\uDCDD 数据浏览",d.querySelector("#tab-btn-struct").textContent="view"===x.activeType?"⚙️ 视图结构":"⚙️ 表结构",window.innerWidth<=480&&m.sidebar.classList.remove("open"))}),m.tabs.forEach(e=>{e.addEventListener("click",()=>{let t=e.dataset.tab;x.activeTab=t,m.tabs.forEach(e=>e.classList.remove("active")),e.classList.add("active"),m.tabContents.forEach(e=>e.classList.remove("active")),d.querySelector(`#tab-${t}`).classList.add("active"),"sql"===t&&!m.sqlInput.value&&x.activeTable&&(m.sqlInput.value=`SELECT * FROM "${x.activeTable}" LIMIT 10;`)})}),m.menuToggle.addEventListener("click",()=>{m.sidebar.classList.toggle("open")});let M;m.searchInput.addEventListener("input",e=>{clearTimeout(M),M=setTimeout(()=>{x.searchTerm=e.target.value,x.currentPage=1,E()},300)}),m.pagePrev.addEventListener("click",()=>{x.currentPage>1&&(x.currentPage--,E())}),m.pageNext.addEventListener("click",()=>{x.currentPage++,E()}),m.refreshBtn.addEventListener("click",()=>{E(),k("数据已刷新")}),m.exportCsvBtn.addEventListener("click",()=>{try{let e=`SELECT * FROM "${x.activeTable}"`,t=[];if(x.searchTerm){let a=r.exec(`PRAGMA table_info("${x.activeTable}")`);if(a.length>0){let d=a[0].values.map(e=>e[1]),o=d.map(e=>`CAST("${e}" AS TEXT) LIKE ?`).join(" OR ");e+=` WHERE ${o}`;for(let l=0;l<d.length;l++)t.push(`%${x.searchTerm}%`)}}let i=r.exec(e,t);i.length>0?R(i[0].columns,i[0].values):k("没有数据可导出")}catch(n){k("导出查询失败: "+n.message)}});let B=null;m.sqlRunBtn.addEventListener("click",()=>{let e=m.sqlInput.value.trim();if(e)try{let t=r.exec(e);if(t.length>0){let a=`
                        <div style="padding: 10px; background: #fafafa; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; flex-shrink: 0; align-items: center;">
                            <span style="color: #2e7d32; font-size: 13px;">✅ 执行成功，返回 ${(B=t[t.length-1]).values.length} 条记录</span>
                            <button class="db-btn db-btn-secondary" id="db-sql-export" style="padding: 4px 8px; font-size: 12px;">📥 复制 Base64 CSV</button>
                        </div>
                        <div class="db-table-container" style="flex: 1;" id="db-sql-table"></div>
                    `;m.sqlResults.innerHTML=a;let d=m.sqlResults.querySelector("#db-sql-table");L(B.columns,B.values,d),m.sqlResults.querySelector("#db-sql-export").addEventListener("click",()=>{R(B.columns,B.values)})}else B=null,m.sqlResults.innerHTML=`<div class="db-message" style="color: #2e7d32;">✅ 语句执行成功，无数据返回 (可能是 UPDATE/INSERT/DELETE)。</div>`}catch(o){B=null,m.sqlResults.innerHTML=`<div class="db-error">❌ 执行错误: <br><br>${y(o.message)}</div>`}}),E(),C()}catch(P){d.innerHTML=`
            <div class="db-header"><h2>🗄️ 数据库预览器</h2></div>
            <div class="db-message">
                <div class="db-error">
                    <h3>❌ 初始化失败</h3>
                    <p>${P.message}</p>
                    <p>请确保网络连接正常以加载依赖库 (sql.js)，或检查文件是否为有效的 SQLite 数据库。</p>
                </div>
            </div>
        `}return d}