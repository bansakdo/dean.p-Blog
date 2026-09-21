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
    background.forEach(element => { element.inert = open && mobile.matches; });
    if (open && mobile.matches) {
      sidebar.setAttribute('role', 'dialog');
      sidebar.setAttribute('aria-modal', 'true');
    } else {
      sidebar.removeAttribute('role');
      sidebar.removeAttribute('aria-modal');
    }
    if (focus) (open ? sidebar.querySelector('button') : toggle).focus();
  };
  toggle.addEventListener('click', () => setOpen(!open, true));
  sidebar.querySelector('.sidebar-close').addEventListener('click', () => setOpen(false, true));
  backdrop.addEventListener('click', () => setOpen(false, true));
  document.addEventListener('keydown', event => {
    if (!open) return;
    if (event.key === 'Escape') { event.preventDefault(); setOpen(false, true); }
    if (event.key !== 'Tab' || !mobile.matches) return;
    const items = [...sidebar.querySelectorAll('a[href], button')].filter(el => el.getClientRects().length);
    const first = items[0], last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || !sidebar.contains(document.activeElement))) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !sidebar.contains(document.activeElement))) {
      event.preventDefault(); first.focus();
    }
  });
  mobile.addEventListener('change', () => {
    const restoreFocus = sidebar.contains(document.activeElement);
    setOpen(!mobile.matches);
    if (!open && restoreFocus) toggle.focus();
  });
  setOpen(!mobile.matches);
})();
