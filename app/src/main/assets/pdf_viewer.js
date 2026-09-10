async function render(fileName, fileExt, content) {
    const root = document.createElement('div');
    root.style.cssText = 'height:100vh;display:flex;flex-direction:column;background:#2f343b;color:#f6f8fa;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif;overflow:hidden;';
    root.innerHTML = `
        <style>
            .pdf-toolbar{height:48px;display:flex;align-items:center;gap:8px;padding:0 10px;background:#1f2328;border-bottom:1px solid #444c56;box-sizing:border-box;flex-shrink:0}
            .pdf-title{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:600}
            .pdf-btn{height:32px;min-width:32px;border:1px solid #59636e;background:#30363d;color:#f0f3f6;border-radius:6px;padding:0 10px}
            .pdf-btn:disabled{opacity:.45}.pdf-page-label{font-size:13px;color:#c9d1d9}
            .pdf-scroll{flex:1;overflow:auto;padding:16px;box-sizing:border-box;text-align:center}
            .pdf-page{display:block;margin:0 auto 16px;background:#fff;box-shadow:0 3px 18px rgba(0,0,0,.35);max-width:100%;height:auto}
            .pdf-error{margin:16px;padding:12px;border:1px solid #f85149;background:#3a1f1f;color:#ffd8d8;border-radius:6px;white-space:pre-wrap;text-align:left}
        </style>
        <div class="pdf-toolbar">
            <button class="pdf-btn" id="pdf-prev">上一页</button>
            <button class="pdf-btn" id="pdf-next">下一页</button>
            <span class="pdf-page-label" id="pdf-status">加载中...</span>
            <div class="pdf-title"></div>
            <button class="pdf-btn" id="pdf-zoom-out">-</button>
            <button class="pdf-btn" id="pdf-zoom-in">+</button>
        </div>
        <div class="pdf-scroll" id="pdf-scroll"></div>
    `;
    root.querySelector('.pdf-title').textContent = fileName || 'PDF';

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

    const scroll = root.querySelector('#pdf-scroll');
    const status = root.querySelector('#pdf-status');
    let pdf = null, page = 1, scale = 1.25, rendering = false;

    async function renderPage() {
        if (!pdf || rendering) return;
        rendering = true;
        try {
            status.textContent = page + ' / ' + pdf.numPages;
            const pdfPage = await pdf.getPage(page);
            const viewport = pdfPage.getViewport({ scale });
            const canvas = document.createElement('canvas');
            canvas.className = 'pdf-page';
            canvas.width = Math.floor(viewport.width);
            canvas.height = Math.floor(viewport.height);
            await pdfPage.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
            scroll.innerHTML = '';
            scroll.appendChild(canvas);
            root.querySelector('#pdf-prev').disabled = page <= 1;
            root.querySelector('#pdf-next').disabled = page >= pdf.numPages;
        } finally {
            rendering = false;
        }
    }

    try {
        const pdfjsLib = await loadScript('https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js', 'pdfjsLib');
        pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
        pdf = await pdfjsLib.getDocument({ data: content }).promise;
        root.querySelector('#pdf-prev').onclick = async () => { if (page > 1) { page--; await renderPage(); } };
        root.querySelector('#pdf-next').onclick = async () => { if (page < pdf.numPages) { page++; await renderPage(); } };
        root.querySelector('#pdf-zoom-out').onclick = async () => { scale = Math.max(.5, scale - .25); await renderPage(); };
        root.querySelector('#pdf-zoom-in').onclick = async () => { scale = Math.min(3, scale + .25); await renderPage(); };
        await renderPage();
    } catch (e) {
        scroll.innerHTML = '<div class="pdf-error">PDF 预览失败\\n' + String(e && e.message ? e.message : e) + '</div>';
        status.textContent = '加载失败';
    }
    return root;
}
