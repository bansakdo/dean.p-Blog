package com.deanp.blog.publishing.persistence.repository;

import com.deanp.blog.publishing.persistence.entity.BoardRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BoardRevisionRepository extends JpaRepository<BoardRevision, UUID> {
}
