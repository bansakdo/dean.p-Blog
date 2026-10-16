(() => {
  const MERMAID_URL = 'https://cdn.jsdelivr.net/npm/mermaid@12.1.0/dist/mermaid.min.js';
  const root = document.documentElement;
  const theme = () => (root.dataset.theme === 'dark' ? 'dark' : 'light');
  let mermaidLoading;

  // Mermaid는 무거우므로 본문에 다이어그램이 있을 때만 한 번 불러온다.
  const loadMermaid = () => {
    if (window.mermaid) return Promise.resolve(window.mermaid);
    mermaidLoading ??= new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = MERMAID_URL;
      script.async = true;
      script.onload = () => resolve(window.mermaid);
      script.onerror = () => { mermaidLoading = undefined; reject(new Error('Mermaid를 불러오지 못했습니다.')); };
      document.head.append(script);
    });
    return mermaidLoading;
  };

  // 원문은 처음 본 글자를 data-source에 보관해 두고, 테마가 바뀌면 원문에서 다시 그린다.
  const renderMermaid = async (scope) => {
    const nodes = [...scope.querySelectorAll('pre.mermaid')];
    if (nodes.length === 0) return;
    nodes.forEach((node) => {
      node.dataset.source ??= node.textContent;
      node.removeAttribute('data-processed');
      node.textContent = node.dataset.source;
    });
    try {
      const mermaid = await loadMermaid();
      // strict: 다이어그램 글자 안의 HTML·클릭 스크립트를 막는다.
      mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: theme() === 'dark' ? 'dark' : 'default' });
      await mermaid.run({ nodes, suppressErrors: true });
    } catch {
      // 불러오지 못하면 원문을 코드처럼 그대로 보여 준다.
    }
  };

  // Archify iframe은 블로그 테마를 따라가도록 주소의 theme 값을 맞춘다.
  const syncArchify = (scope) => {
    scope.querySelectorAll('iframe[data-archify-src]').forEach((frame) => {
      const src = `${frame.dataset.archifySrc}?embed=1&theme=${theme()}`;
      if (frame.getAttribute('src') !== src) frame.setAttribute('src', src);
    });
  };

  // 코드 블록에 언어 이름과 복사 버튼을 붙인다.
  const decorateCode = (scope) => {
    scope.querySelectorAll('pre > code').forEach((code) => {
      const pre = code.parentElement;
      if (pre.parentElement?.classList.contains('code-block')) return;
      const wrapper = document.createElement('div');
      wrapper.className = 'code-block';
      pre.replaceWith(wrapper);
      wrapper.append(pre);
      const language = [...code.classList].find((name) => name.startsWith('language-'))?.slice(9);
      if (language) {
        const label = document.createElement('span');
        label.className = 'code-language';
        label.textContent = language;
        wrapper.append(label);
      }
      const copy = document.createElement('button');
      copy.type = 'button';
      copy.className = 'code-copy';
      copy.textContent = '복사';
      copy.setAttribute('aria-label', '코드 복사');
      copy.addEventListener('click', async () => {
        try {
          await navigator.clipboard.writeText(code.textContent);
          copy.textContent = '복사됨';
        } catch {
          copy.textContent = '복사 실패';
        }
        setTimeout(() => { copy.textContent = '복사'; }, 1500);
      });
      wrapper.append(copy);
    });
  };

  // 본문(공개 화면, 편집기 미리보기)의 코드·다이어그램을 꾸민다.
  const enhanceContent = (scope = document) => {
    if (window.hljs) {
      scope.querySelectorAll('pre code:not(.hljs)').forEach((block) => {
        window.hljs.highlightElement(block);
      });
    }
    decorateCode(scope);
    syncArchify(scope);
    renderMermaid(scope);
  };
  window.deanpEnhanceContent = enhanceContent;

  const initializeLibraries = () => {
    enhanceContent(document);
    if (window.lucide) {
      window.lucide.createIcons();
    }
    // 테마를 바꾸면 Mermaid를 다시 그리고 Archify 테마를 맞춘다.
    new MutationObserver(() => {
      syncArchify(document);
      renderMermaid(document);
    }).observe(root, { attributes: true, attributeFilter: ['data-theme'] });
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeLibraries, { once: true });
  } else {
    initializeLibraries();
  }
})();
