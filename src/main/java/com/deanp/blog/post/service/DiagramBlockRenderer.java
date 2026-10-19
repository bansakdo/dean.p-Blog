package com.deanp.blog.post.service;

import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.util.sequence.Escaping;

import java.util.Set;
import java.util.UUID;

/**
 * 본문의 ```mermaid 와 ```archify 코드 블록을 다이어그램 자리로 바꾸고, 나머지 코드 블록은 기본 렌더링에 맡긴다.
 * 모든 글자는 이스케이프하므로 본문에 HTML을 넣을 수 없다는 렌더러 원칙을 그대로 지킨다.
 */
final class DiagramBlockRenderer implements NodeRenderer {

    /** iframe 안 다이어그램에 주는 권한. 같은 출처 권한(allow-same-origin)은 주지 않는다. */
    static final String IFRAME_SANDBOX = "allow-scripts allow-downloads allow-popups";

    /** @return 코드 블록 렌더링 처리기 */
    @Override
    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
        return Set.of(new NodeRenderingHandler<>(FencedCodeBlock.class, this::render));
    }

    /**
     * 언어 표시가 mermaid 또는 archify이면 다이어그램으로, 아니면 기본 코드 블록으로 그린다.
     *
     * @param block 코드 블록
     * @param context 렌더링 문맥
     * @param html 출력
     */
    private void render(FencedCodeBlock block, NodeRendererContext context, HtmlWriter html) {
        String info = block.getInfo().toString().strip();
        String language = info.split("\\s+", 2)[0];
        if ("mermaid".equalsIgnoreCase(language)) {
            // Mermaid는 브라우저에서 .mermaid 요소의 글자를 읽어 그린다. 스크립트가 없으면 원문 그대로 보인다.
            // pre 보존 모드로 써야 줄 앞 들여쓰기가 지워지지 않는다.
            html.line().raw("<pre class=\"mermaid\">").openPre()
                    .text(block.getContentChars().normalizeEOL())
                    .raw("</pre>").closePre().line();
            return;
        }
        if ("archify".equalsIgnoreCase(language)) {
            UUID id = parseId(block.getContentChars().toString().strip());
            if (id != null) {
                String caption = info.length() > language.length() ? info.substring(language.length()).strip() : "";
                renderArchify(id, caption, html);
                return;
            }
        }
        context.delegateRender();
    }

    /**
     * 격리한 iframe과 새 창 링크로 Archify 다이어그램을 넣는다. 블로그 테마를 붙인 주소는 브라우저 스크립트가
     * 한 번만 넣으므로(두 번 내려받지 않도록) src는 비워 둔다. 스크립트가 없으면 새 창 링크로 볼 수 있다.
     *
     * @param id 다이어그램 첨부 파일 식별자
     * @param caption 언어 표시 뒤에 적은 설명, 없으면 빈 문자열
     * @param html 출력
     */
    private void renderArchify(UUID id, String caption, HtmlWriter html) {
        String url = "/media/diagrams/" + id;
        String title = caption.isEmpty() ? "Archify 다이어그램" : caption;
        html.line().raw("<figure class=\"archify-embed\">")
                .raw("<iframe data-archify-src=\"" + url + "\"")
                .raw(" title=\"" + Escaping.escapeHtml(title, false) + "\"")
                .raw(" sandbox=\"" + IFRAME_SANDBOX + "\" allowfullscreen loading=\"lazy\" referrerpolicy=\"no-referrer\"></iframe>")
                .raw("<figcaption>");
        if (!caption.isEmpty()) {
            html.raw("<span>" + Escaping.escapeHtml(caption, false) + "</span> ");
        }
        html.raw("<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener\">새 창에서 크게 보기</a>")
                .raw("</figcaption></figure>").line();
    }

    /**
     * @param value 블록 안에 적은 글자
     * @return UUID, 형식이 다르면 null
     */
    private static UUID parseId(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return null;
        }
        return UUID.fromString(value);
    }
}
