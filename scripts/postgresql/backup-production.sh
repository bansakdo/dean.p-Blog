#!/bin/sh
# 목적: Synology의 운영 PostgreSQL 데이터베이스를 암호 노출 없이 커스텀 아카이브로 백업한다.
# 사용: BACKUP_DIR=/volume1/Backup/PostgreSQL/Database/dean_p_blog BACKUP_PGPASS_FILE=/보호된/경로/.pgpass sh scripts/postgresql/backup-production.sh
# 설계: 실행 중인 DB 컨테이너의 이미지와 네트워크를 일회성 클라이언트에 공유하고, 검증된 아카이브만 확정한다.
# 관련 스크립트: bootstrap.sql
set -eu
umask 077

: "${BACKUP_DIR:?백업 디렉터리를 지정해야 합니다}"
: "${BACKUP_PGPASS_FILE:?백업 계정의 .pgpass 파일을 지정해야 합니다}"
DB_CONTAINER=${DB_CONTAINER:-postgres}
DOCKER_BIN=${DOCKER_BIN:-docker}

test -d "$BACKUP_DIR" || { printf '백업 디렉터리가 없습니다.\n' >&2; exit 1; }
test -f "$BACKUP_PGPASS_FILE" || { printf '.pgpass 파일이 없습니다.\n' >&2; exit 1; }
case "$(stat -c %a "$BACKUP_PGPASS_FILE")" in
    400|600) ;;
    *) printf '.pgpass 파일 권한은 0400 또는 0600이어야 합니다.\n' >&2; exit 1 ;;
esac
[ "$("$DOCKER_BIN" inspect --format '{{.State.Running}}' "$DB_CONTAINER")" = true ] || {
    printf '운영 PostgreSQL 컨테이너가 실행 중이지 않습니다.\n' >&2
    exit 1
}
IMAGE_ID=$("$DOCKER_BIN" inspect --format '{{.Image}}' "$DB_CONTAINER")

name="dean_p_blog-$(date -u +%Y%m%dT%H%M%SZ).dump"
archive="$BACKUP_DIR/$name"
partial="$BACKUP_DIR/.$name.$$.partial"
[ ! -e "$archive" ] && [ ! -e "$partial" ] || {
    printf '동일한 이름의 백업 파일이 이미 있습니다.\n' >&2
    exit 1
}
trap 'rm -f "$partial"' 0
trap 'exit 1' 1 2 3 15

"$DOCKER_BIN" run --rm --user 0:0 --network "container:$DB_CONTAINER" \
    --mount "type=bind,src=$BACKUP_DIR,dst=/backup" \
    --mount "type=bind,src=$BACKUP_PGPASS_FILE,dst=/run/secrets/pgpass,readonly" \
    --env PGPASSFILE=/run/secrets/pgpass \
    --entrypoint pg_dump "$IMAGE_ID" \
    --host 127.0.0.1 --port 5432 --username dean_p_blog_app_backup \
    --dbname dean_p_blog --format custom --file "/backup/$(basename "$partial")"

test -s "$partial" || { printf '백업 파일이 비어 있습니다.\n' >&2; exit 1; }
"$DOCKER_BIN" run --rm --network none \
    --mount "type=bind,src=$BACKUP_DIR,dst=/backup,readonly" \
    --entrypoint pg_restore "$IMAGE_ID" --list "/backup/$(basename "$partial")" >/dev/null
mv "$partial" "$archive"
trap - 0 1 2 3 15
printf 'BACKUP_OK %s\n' "$archive"
