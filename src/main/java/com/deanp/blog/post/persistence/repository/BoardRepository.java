package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {
}
