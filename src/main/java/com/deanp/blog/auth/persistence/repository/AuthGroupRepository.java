package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.AuthGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuthGroupRepository extends JpaRepository<AuthGroup, UUID> {
}
