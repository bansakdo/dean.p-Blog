package com.deanp.blog.media.persistence.repository;

import com.deanp.blog.media.persistence.entity.AttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 첨부 파일 메타데이터 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface AttachedFileRepository extends JpaRepository<AttachedFile, UUID> {
}
