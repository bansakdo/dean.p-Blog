package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.BoardDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BoardDetailRepository extends JpaRepository<BoardDetail, UUID> {
}
