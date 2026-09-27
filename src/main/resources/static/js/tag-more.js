(() => {
  const button = document.querySelector('[data-tag-more]');
  const list = document.querySelector('.tag-chip-links');
  const error = document.querySelector('[data-tag-error]');
  if (!button || !list || !error) return;

  const filters = new URLSearchParams(location.search);
  button.addEventListener('click', async () => {
    button.disabled = true;
    error.hidden = true;
    try {
      const offset = Number(button.dataset.offset);
      const response = await fetch(`/posts/tags/more?offset=${offset}`, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('태그 조회 실패');
      const page = await response.json();

      for (const tag of page.tags) {
        const url = new URL('/posts', location.origin);
        for (const key of ['category', 'series', 'q']) {
          if (filters.has(key)) url.searchParams.set(key, filters.get(key));
        }
        url.searchParams.set('tag', tag.slug);
        const link = document.createElement('a');
        link.href = url.toString();
        link.textContent = tag.name;
        if (filters.get('tag') === tag.slug) {
          link.classList.add('is-active');
          link.setAttribute('aria-current', 'true');
        }
        list.append(link);
      }
      button.dataset.offset = String(offset + page.tags.length);
      button.hidden = !page.hasMore;
    } catch {
      error.hidden = false;
    } finally {
      button.disabled = false;
    }
  });
})();
