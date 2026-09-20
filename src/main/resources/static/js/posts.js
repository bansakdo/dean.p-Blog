(() => {
  const panel = document.querySelector('.posts-page .browse-panel');
  if (!panel) return;
  const mobile = window.matchMedia('(max-width: 720px)');
  const update = () => { panel.open = !mobile.matches; };
  update();
  mobile.addEventListener('change', update);
})();
