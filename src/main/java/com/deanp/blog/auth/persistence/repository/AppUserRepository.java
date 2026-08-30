package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
