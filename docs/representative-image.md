# 대표 이미지

- `post_detail.representative_image_id`는 선택값이며 본문 Markdown과 독립적이다.
- V7 복합 외래키는 같은 게시글의 `post_attached_file` 연결만 허용한다. 연결 해제/파일 삭제 전 대표 이미지 값을 NULL로 변경해야 한다.
- 목록 조회는 attached_files를 LEFT JOIN하여 저장 파일명을 얻는다. 현재 저장 규약 posts/{postDetailId}/{storedName}으로 URL을 구성한다.
- 대표 이미지 미지정 시 텍스트만 표시하며 본문 첫 이미지 자동 추출은 하지 않는다.
- 홈은 대표 이미지를 object-fit:cover로 표시한다. `/posts`는 분류·시리즈 탐색 시안에 맞춘 텍스트 목록으로 대표 이미지를 표시하지 않는다. 원본 파일 및 본문은 변경하지 않는다.
- 작성/업로드/대표 이미지 선택 UI는 미구현이며 지금은 DB에서 지정한다.
- 저장소 루트는 `app.media.storage-root`이며 파일은 그 아래 `posts/{postDetailId}/{storedName}`에 둔다. `media.controller.PublicMediaConfiguration`은 `/media/posts/**`만 이 디렉터리에서 제공한다. 로컬 dev의 기본 저장소는 `../dean-p-blog-media`, Docker 개발 서버는 `/app/media`이다. 게시글 외부 파일은 이 URL로 제공하지 않는다.
- 과거의 `media/posts` 중첩 폴더는 새 위치로 복사하고 응답을 확인한 뒤 별도로 정리한다. 운영 서버는 아직 새 코드와 파일을 배포하지 않았다.
- 검증: 게시글/미디어 Gradle 테스트, 실제 PostgreSQL V7 적용·잘못된 연결 거부·본문 보존, 미지정 목록 이미지 없음, PC/모바일 썸네일 표시.
