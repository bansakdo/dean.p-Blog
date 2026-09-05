package com.deanp.blog.menu.persistence.repository;

import com.deanp.blog.menu.persistence.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 메뉴 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface MenuRepository extends JpaRepository<Menu, UUID> {
}
