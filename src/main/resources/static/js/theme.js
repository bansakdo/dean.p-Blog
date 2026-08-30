(() => {
  const root = document.documentElement;
  const button = document.querySelector('[data-theme-toggle]');
  if (!button) return;

  const updateLabel = () => {
    const dark = root.dataset.theme === 'dark';
    button.setAttribute('aria-label', dark ? '라이트 모드로 전환' : '다크 모드로 전환');
    button.setAttribute('aria-pressed', String(dark));
  };

  button.addEventListener('click', () => {
    const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
    root.dataset.theme = next;
    localStorage.setItem('deanp-theme', next);
    updateLabel();
  });

  updateLabel();
})();
