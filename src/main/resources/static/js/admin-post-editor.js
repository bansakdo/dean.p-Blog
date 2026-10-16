// 삭제처럼 되돌리기 번거로운 요청은 제출 전에 한 번 더 묻는다.
document.querySelectorAll('form[data-confirm]').forEach((form) => {
  form.addEventListener('submit', (event) => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});

// 발행 시각이 미래이면 발행 버튼이 예약 발행임을 알 수 있게 이름을 바꾸고, 공개 중인 글을 저장으로 예약 전환할 때는 한 번 더 묻는다.
(() => {
  const input = document.querySelector('#post-published-at');
  if (!input) return;
  // 서버와 같이 한국 시간으로 해석한다.
  const isFuture = () => Boolean(input.value) && new Date(`${input.value}:00+09:00`) > new Date();
  const button = document.querySelector('[data-publish-button]');
  if (button) {
    const update = () => { button.textContent = isFuture() ? '예약 발행' : '발행'; };
    input.addEventListener('input', update);
    update();
  }
  if (input.dataset.live === 'true' && input.form) {
    input.form.addEventListener('submit', (event) => {
      if (event.submitter?.value === 'save' && isFuture()
          && !window.confirm('발행 시각이 미래라서 저장하면 그 시각까지 공개 화면에서 내려갑니다. 저장할까요?')) {
        event.preventDefault();
      }
    });
  }
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

// 본문 넣기 도구: 코드 블록·Mermaid 틀과 Archify 다이어그램 블록을 커서 위치에 넣고, 저장하지 않은 본문을 미리 본다.
(() => {
  const content = document.querySelector('#post-content');
  if (!content) return;

  // 커서 위치에 블록을 넣는다. 블록 앞뒤는 빈 줄로 띄우고, 선택한 글이 있으면 블록 안에 넣은 뒤 그 안쪽을 선택한다.
  const insertBlock = (opening, body, closing = '```') => {
    const start = content.selectionStart;
    const end = content.selectionEnd;
    const selected = content.value.slice(start, end);
    const before = content.value.slice(0, start);
    const lead = before === '' || before.endsWith('\n\n') ? '' : (before.endsWith('\n') ? '\n' : '\n\n');
    const inner = selected || body;
    const text = `${lead}${opening}\n${inner}\n${closing}\n`;
    content.setRangeText(text, start, end, 'end');
    const innerStart = start + lead.length + opening.length + 1;
    content.setSelectionRange(innerStart, innerStart + inner.length);
    content.focus();
    content.dispatchEvent(new Event('input', { bubbles: true }));
  };

  const language = document.querySelector('#post-code-language');
  document.querySelector('[data-insert="code"]')?.addEventListener('click', () => {
    insertBlock(`\`\`\`${language?.value || ''}`, '코드를 입력하세요');
  });
  document.querySelector('[data-insert="mermaid"]')?.addEventListener('click', () => {
    insertBlock('```mermaid', 'flowchart LR\n  A[시작] --> B{조건}\n  B -->|예| C[처리]\n  B -->|아니오| D[끝]');
  });

  // 다이어그램 목록의 '본문에 넣기'와 업로드 직후 넣기는 같은 블록을 쓴다.
  const insertDiagram = (markdown) => {
    const [opening, ...rest] = markdown.split('\n');
    insertBlock(opening, rest.slice(0, -1).join('\n'));
  };
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-insert-diagram]');
    if (button) insertDiagram(button.dataset.insertDiagram);
  });

  const upload = document.querySelector('[data-diagram-upload]');
  const status = document.querySelector('[data-diagram-status]');
  const list = document.querySelector('[data-diagram-list]');
  if (upload && status && list) {
    upload.addEventListener('change', async () => {
      const file = upload.files[0];
      if (!file) return;
      const body = new FormData();
      body.append('file', file);
      upload.disabled = true;
      status.textContent = '올리는 중입니다…';
      try {
        const response = await fetch(upload.dataset.diagramUpload, {
          method: 'POST',
          headers: { [upload.dataset.csrfHeader]: upload.dataset.csrfToken },
          body
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.message || '다이어그램을 올리지 못했습니다.');
        insertDiagram(result.markdown);

        const item = document.createElement('li');
        const name = document.createElement('span');
        const code = document.createElement('code');
        const insert = document.createElement('button');
        const open = document.createElement('a');
        name.textContent = result.name;
        code.textContent = result.id;
        insert.type = 'button';
        insert.className = 'admin-text-button';
        insert.dataset.insertDiagram = result.markdown;
        insert.textContent = '본문에 넣기';
        open.href = `/media/diagrams/${result.id}`;
        open.target = '_blank';
        open.rel = 'noopener';
        open.textContent = '열기';
        item.append(name, ' ', code, ' ', insert, ' ', open);
        list.append(item);
        status.textContent = '본문에 다이어그램을 넣었습니다. 저장해야 반영됩니다.';
      } catch (error) {
        status.textContent = error.message;
      } finally {
        upload.disabled = false;
        upload.value = '';
      }
    });
  }

  // 미리보기: 켜 두면 입력을 잠시 멈췄을 때 서버가 공개 화면과 같은 방식으로 바꾼 HTML을 보여 준다.
  const toggle = document.querySelector('[data-preview-toggle]');
  const preview = document.querySelector('[data-preview]');
  if (!toggle || !preview) return;
  let timer;
  let latest = 0;
  const render = async () => {
    const request = ++latest;
    try {
      const response = await fetch(toggle.dataset.previewUrl, {
        method: 'POST',
        headers: {
          [toggle.dataset.csrfHeader]: toggle.dataset.csrfToken,
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        },
        body: new URLSearchParams({ content: content.value })
      });
      if (!response.ok) throw new Error();
      const html = await response.text();
      // 늦게 도착한 이전 응답이 최신 미리보기를 덮지 않게 한다. 서버가 본문 HTML을 이스케이프해 돌려준다.
      if (request !== latest) return;
      update(html);
      window.deanpEnhanceContent?.(preview);
    } catch {
      if (request === latest) preview.textContent = '미리보기를 만들지 못했습니다.';
    }
  };
  // 바뀐 블록만 갈아 끼운다. 같은 자리의 같은 Archify 다이어그램은 그대로 두어 입력할 때마다 다시 내려받지 않게 한다.
  const update = (html) => {
    const next = document.createElement('template');
    next.innerHTML = html;
    const oldNodes = [...preview.childNodes];
    const newNodes = [...next.content.childNodes];
    const sameDiagram = (a, b) => a?.nodeType === 1 && b?.nodeType === 1
      && a.matches('figure.archify-embed') && b.matches('figure.archify-embed')
      && a.querySelector('iframe')?.dataset.archifySrc === b.querySelector('iframe')?.dataset.archifySrc
      && a.querySelector('figcaption')?.isEqualNode(b.querySelector('figcaption'));
    newNodes.forEach((node, index) => {
      const old = oldNodes[index];
      if (old && sameDiagram(old, node)) return;
      if (old) old.replaceWith(node); else preview.append(node);
    });
    oldNodes.slice(newNodes.length).forEach((node) => node.remove());
  };
  toggle.addEventListener('click', () => {
    const on = toggle.getAttribute('aria-pressed') !== 'true';
    toggle.setAttribute('aria-pressed', String(on));
    preview.hidden = !on;
    if (on) render();
  });
  content.addEventListener('input', () => {
    if (preview.hidden) return;
    clearTimeout(timer);
    timer = setTimeout(render, 400);
  });
})();
