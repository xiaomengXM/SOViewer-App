async function render(fileName, fileExt, content) {
    const root = document.createElement('div');
    root.style.cssText = 'height:100vh;display:flex;flex-direction:column;background:#fff;color:#24292f;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif;overflow:hidden;';

    const style = document.createElement('style');
    style.textContent = `
        .md-toolbar{height:48px;display:flex;align-items:center;gap:8px;padding:0 12px;border-bottom:1px solid #d0d7de;background:#f6f8fa;box-sizing:border-box;flex-shrink:0}
        .md-title{font-weight:600;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}
        .md-btn{height:32px;border:1px solid #d0d7de;background:#fff;border-radius:6px;padding:0 10px;color:#24292f}
        .md-body-wrap{flex:1;min-height:0;display:flex;position:relative}
        .md-toc{width:280px;border-right:1px solid #d0d7de;background:#f6f8fa;overflow:auto;padding:10px 0;box-sizing:border-box;flex-shrink:0}
        .md-toc.hidden{display:none}
        .md-toc-title{font-size:12px;font-weight:700;color:#57606a;padding:0 14px 8px}
        .md-toc-item{display:block;border:0;background:transparent;width:100%;text-align:left;color:#0969da;font-size:13px;line-height:1.35;padding:6px 14px;box-sizing:border-box;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .md-toc-item:hover,.md-toc-item.active{background:#eaeef2}
        .md-scroll{flex:1;min-width:0;overflow:auto;padding:28px;box-sizing:border-box;scroll-behavior:smooth}
        .markdown-body{max-width:920px;margin:0 auto;line-height:1.58;font-size:16px;word-wrap:break-word}
        .markdown-body h1,.markdown-body h2,.markdown-body h3,.markdown-body h4,.markdown-body h5,.markdown-body h6{margin:24px 0 14px;font-weight:650;line-height:1.25;scroll-margin-top:18px}
        .markdown-body h1{font-size:2em;border-bottom:1px solid #d8dee4;padding-bottom:.3em}.markdown-body h2{font-size:1.5em;border-bottom:1px solid #d8dee4;padding-bottom:.3em}
        .markdown-body h3{font-size:1.25em}.markdown-body h4{font-size:1em}.markdown-body h5{font-size:.875em}.markdown-body h6{font-size:.85em;color:#57606a}
        .markdown-body p,.markdown-body ul,.markdown-body ol,.markdown-body blockquote,.markdown-body table,.markdown-body pre{margin-top:0;margin-bottom:16px}
        .markdown-body a{color:#0969da;text-decoration:none}.markdown-body a:hover{text-decoration:underline}
        .markdown-body code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:85%;background:rgba(175,184,193,.2);border-radius:6px;padding:.2em .4em}
        .markdown-body pre{background:#f6f8fa;border-radius:6px;padding:16px;overflow:auto}.markdown-body pre code{background:transparent;padding:0}
        .markdown-body blockquote{padding:0 1em;color:#57606a;border-left:.25em solid #d0d7de}
        .markdown-body table{display:block;max-width:100%;overflow:auto;border-collapse:collapse}.markdown-body th,.markdown-body td{border:1px solid #d0d7de;padding:6px 13px}.markdown-body tr:nth-child(2n){background:#f6f8fa}
        .markdown-body img{max-width:100%;height:auto}
        .md-error{margin:16px;padding:12px;border:1px solid #ffb3b3;background:#fff5f5;color:#b42318;border-radius:6px;white-space:pre-wrap}
        @media(max-width:720px){.md-toc{position:absolute;left:0;top:0;bottom:0;z-index:10;box-shadow:0 8px 24px rgba(140,149,159,.35)}.md-scroll{padding:18px}.markdown-body{font-size:15px}}
    `;
    root.appendChild(style);

    root.innerHTML += `
        <div class="md-toolbar">
            <button class="md-btn" id="md-toggle">目录</button>
            <div class="md-title"></div>
        </div>
        <div class="md-body-wrap">
            <nav class="md-toc" id="md-toc"><div class="md-toc-title">目录</div><div id="md-toc-list"></div></nav>
            <main class="md-scroll" id="md-scroll"><div class="markdown-body" id="md-content"></div></main>
        </div>
    `;
    root.querySelector('.md-title').textContent = fileName || 'Markdown';

    function loadScript(url, globalName) {
        return new Promise((resolve, reject) => {
            if (window[globalName]) return resolve(window[globalName]);
            const s = document.createElement('script');
            s.src = url;
            s.onload = () => window[globalName] ? resolve(window[globalName]) : reject(new Error(globalName + ' 未初始化'));
            s.onerror = () => reject(new Error('依赖加载失败: ' + url));
            document.head.appendChild(s);
        });
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    }

    try {
        const text = new TextDecoder('utf-8').decode(content || new Uint8Array());
        await Promise.all([
            loadScript('https://cdnjs.cloudflare.com/ajax/libs/marked/4.3.0/marked.min.js', 'marked'),
            loadScript('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.8.0/highlight.min.js', 'hljs').catch(() => null)
        ]);

        const headings = [];
        const used = {};
        function slugify(raw) {
            let base = String(raw || '').replace(/<[^>]+>/g, '').trim().toLowerCase()
                .replace(/[\u2000-\u206F\u2E00-\u2E7F'"!#$%&()*+,./:;<=>?@[\\\]^`{|}~]/g, '')
                .replace(/\s+/g, '-');
            if (!base) base = 'heading';
            if (used[base]) {
                used[base] += 1;
                return base + '-' + used[base];
            }
            used[base] = 1;
            return base;
        }

        const renderer = new marked.Renderer();
        renderer.heading = function(textValue, level, raw) {
            const title = String(textValue || '').replace(/<[^>]+>/g, '');
            const id = slugify(raw || title);
            headings.push({ id, level, title });
            return `<h${level} id="${id}">${textValue}</h${level}>`;
        };
        renderer.code = function(code, language) {
            const lang = language && window.hljs && hljs.getLanguage(language) ? language : '';
            let html = escapeHtml(code);
            if (lang) {
                try { html = hljs.highlight(code, { language: lang }).value; } catch (e) {}
            }
            return `<pre><code class="${lang ? 'language-' + lang : ''}">${html}</code></pre>`;
        };
        marked.setOptions({ renderer, gfm: true, breaks: false, headerIds: false, mangle: false });

        const contentEl = root.querySelector('#md-content');
        contentEl.innerHTML = marked.parse(text.trim() ? text : '*该文件为空*');

        const toc = root.querySelector('#md-toc');
        const tocList = root.querySelector('#md-toc-list');
        const scroll = root.querySelector('#md-scroll');
        if (headings.length) {
            const minLevel = Math.min.apply(null, headings.map(h => h.level));
            headings.forEach(h => {
                const btn = document.createElement('button');
                btn.className = 'md-toc-item';
                btn.textContent = h.title || h.id;
                btn.title = btn.textContent;
                btn.style.paddingLeft = (14 + (h.level - minLevel) * 14) + 'px';
                btn.addEventListener('click', () => {
                    const target = contentEl.querySelector('[id="' + String(h.id).replace(/["\\]/g, '\\$&') + '"]');
                    if (target) scroll.scrollTo({ top: Math.max(0, target.offsetTop - 12), behavior: 'smooth' });
                    if (window.innerWidth <= 720) toc.classList.add('hidden');
                });
                tocList.appendChild(btn);
            });
        } else {
            tocList.innerHTML = '<div style="padding:12px 14px;color:#57606a;font-size:13px">未找到标题</div>';
        }
        root.querySelector('#md-toggle').addEventListener('click', () => toc.classList.toggle('hidden'));
    } catch (e) {
        root.querySelector('#md-content').innerHTML = '<div class="md-error">Markdown 预览失败\\n' + escapeHtml(e && e.message ? e.message : e) + '</div>';
    }
    return root;
}
