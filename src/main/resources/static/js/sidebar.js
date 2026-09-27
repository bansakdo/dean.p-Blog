(() => {
  const sidebar = document.querySelector('#site-sidebar');
  const toggle = document.querySelector('.menu-toggle');
  const backdrop = document.querySelector('.sidebar-backdrop');
  if (!sidebar || !toggle || !backdrop) return;
  const mobile = matchMedia('(max-width: 720px)');
  const background = [...document.querySelectorAll('body > main, body > footer')];
  let open = false;
  const setOpen = (next, focus = false) => {
    open = next;
    sidebar.hidden = !open;
    backdrop.hidden = !open || !mobile.matches;
    document.body.classList.toggle('sidebar-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    toggle.textContent = open ? '×' : '☰';
    background.forEach(element => { element.inert = open && mobile.matches; });
    if (focus) (open && mobile.matches ? sidebar.querySelector('.sidebar-close') : toggle).focus();
  };
  toggle.addEventListener('click', () => setOpen(!open, true));
  sidebar.querySelector('.sidebar-close').addEventListener('click', () => setOpen(false, true));
  backdrop.addEventListener('click', () => setOpen(false, true));
  document.addEventListener('keydown', event => {
    if (!open) return;
    if (event.key === 'Escape') { event.preventDefault(); setOpen(false, true); }
  });
  mobile.addEventListener('change', () => {
    const restoreFocus = sidebar.contains(document.activeElement);
    setOpen(false);
    if (!open && restoreFocus) toggle.focus();
  });
  setOpen(false);
})();
