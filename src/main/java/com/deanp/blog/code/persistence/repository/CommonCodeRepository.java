package com.deanp.blog.code.persistence.repository;

import com.deanp.blog.code.persistence.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommonCodeRepository extends JpaRepository<CommonCode, UUID> {
}
