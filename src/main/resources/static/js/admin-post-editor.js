// 삭제처럼 되돌리기 번거로운 요청은 제출 전에 한 번 더 묻는다.
document.querySelectorAll('form[data-confirm]').forEach((form) => {
  form.addEventListener('submit', (event) => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});

// 발행 시각이 미래이면 발행 버튼이 예약 발행임을 알 수 있게 이름을 바꾼다.
(() => {
  const input = document.querySelector('#post-published-at');
  const button = document.querySelector('[data-publish-button]');
  if (!input || !button) return;
  const update = () => {
    const at = input.value ? new Date(input.value) : null;
    button.textContent = at && at > new Date() ? '예약 발행' : '발행';
  };
  input.addEventListener('input', update);
  update();
})();

(() => {
  const input = document.querySelector('[data-image-upload]');
  const content = document.querySelector('#post-content');
  const representative = document.querySelector('#post-representative-image');
  const status = document.querySelector('[data-image-status]');
  const list = document.querySelector('[data-image-list]');
  if (!input || !content || !representative || !status || !list) return;

  input.addEventListener('change', async () => {
    const file = input.files[0];
    if (!file) return;
    const body = new FormData();
    body.append('file', file);
    input.disabled = true;
    status.textContent = '올리는 중입니다…';
    try {
      const response = await fetch(input.dataset.imageUpload, {
        method: 'POST',
        headers: { [input.dataset.csrfHeader]: input.dataset.csrfToken },
        body
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.message || '이미지를 올리지 못했습니다.');

      // 본문의 커서 위치에 이미지 문법을 넣고, 대표 이미지 선택지와 목록에도 추가한다.
      const markdown = `![${result.name.replace(/[\[\]]/g, '')}](${result.url})`;
      content.setRangeText(`${markdown}\n`, content.selectionStart, content.selectionEnd, 'end');
      representative.add(new Option(result.name, result.id));
      const item = document.createElement('li');
      const name = document.createElement('span');
      const code = document.createElement('code');
      name.textContent = result.name;
      code.textContent = `![](${result.url})`;
      item.append(name, ' ', code);
      list.append(item);
      status.textContent = '본문에 이미지를 넣었습니다. 저장해야 반영됩니다.';
      content.focus();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      input.disabled = false;
      input.value = '';
    }
  });
})();
