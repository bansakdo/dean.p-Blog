package com.deanp.blog.code.persistence.repository;

import com.deanp.blog.code.persistence.entity.CommonCodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommonCodeDetailRepository extends JpaRepository<CommonCodeDetail, UUID> {
}
