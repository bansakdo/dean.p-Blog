(() => {
  const root = document.querySelector('.account-menu');
  if (!root) return;
  const toggle = root.querySelector('.account-toggle');
  const panel = root.querySelector('.account-panel');
  const close = (restoreFocus = false) => {
    panel.hidden = true;
    toggle.setAttribute('aria-expanded', 'false');
    if (restoreFocus) toggle.focus();
  };
  toggle.addEventListener('click', () => {
    const open = panel.hidden;
    panel.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
  });
  document.addEventListener('click', event => {
    if (!root.contains(event.target)) close();
  });
  document.addEventListener('focusin', event => {
    if (!root.contains(event.target)) close();
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !panel.hidden) {
      event.preventDefault();
      event.stopImmediatePropagation();
      close(true);
    }
  }, true);
})();
