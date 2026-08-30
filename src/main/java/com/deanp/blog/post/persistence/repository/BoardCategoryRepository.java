package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.BoardCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BoardCategoryRepository extends JpaRepository<BoardCategory, UUID> {
}
