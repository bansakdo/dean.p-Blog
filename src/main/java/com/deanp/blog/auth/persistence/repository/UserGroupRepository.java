package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.auth.persistence.entity.UserGroupId;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
}
