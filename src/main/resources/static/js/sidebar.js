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
    sessionStorage.setItem('deanp-sidebar-open', String(open));
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    toggle.textContent = open ? '×' : '☰';
    background.forEach(element => { element.inert = open && mobile.matches; });
    if (focus) (open && mobile.matches ? sidebar.querySelector('.sidebar-nav a') : toggle).focus();
  };
  toggle.addEventListener('click', () => setOpen(!open, true));
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
  // 글·관리자 메뉴 왼쪽 아이콘으로 하위 메뉴를 접고 편다. 글 화면·관리 화면에서는 펼친 채로 그려진다.
  sidebar.querySelectorAll('[data-group-toggle]').forEach(button => {
    const menu = document.getElementById(button.getAttribute('aria-controls'));
    if (!menu) return;
    button.addEventListener('click', () => {
      const expanded = button.getAttribute('aria-expanded') !== 'true';
      button.setAttribute('aria-expanded', String(expanded));
      button.setAttribute('aria-label', `${button.dataset.groupName} 메뉴 ${expanded ? '접기' : '펼치기'}`);
      menu.hidden = !expanded;
    });
  });
  // 모바일에서 메뉴의 링크를 누르면 메뉴를 접어 본문이 보이게 한다. 펼침 버튼은 링크가 아니라 그대로 둔다.
  sidebar.addEventListener('click', event => {
    if (mobile.matches && event.target.closest('a[href]')) setOpen(false);
  });
  // PC는 열어 둔 메뉴를 다음 화면에서도 유지하고, 모바일은 화면을 새로 열면 항상 접힌 채로 시작한다.
  // 화면을 열 때는 포커스를 옮기지 않아 첫 메뉴(소개)에 포커스 표시가 생기지 않게 한다.
  const savedOpen = sessionStorage.getItem('deanp-sidebar-open') === 'true';
  setOpen(savedOpen && !mobile.matches);
})();
