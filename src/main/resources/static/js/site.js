(() => {
  const initializeLibraries = () => {
    if (window.hljs) {
      document.querySelectorAll('pre code:not(.hljs)').forEach((block) => {
        window.hljs.highlightElement(block);
      });
    }

    if (window.lucide) {
      window.lucide.createIcons();
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeLibraries, { once: true });
  } else {
    initializeLibraries();
  }
})();
