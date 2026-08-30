package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.GroupMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.auth.persistence.entity.GroupMenuId;

public interface GroupMenuRepository extends JpaRepository<GroupMenu, GroupMenuId> {
}
