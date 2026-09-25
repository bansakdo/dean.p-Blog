package com.deanp.blog.code.persistence.repository;

import com.deanp.blog.code.persistence.entity.CommonCodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 공통 코드 상세 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface CommonCodeDetailRepository extends JpaRepository<CommonCodeDetail, UUID> {
}
