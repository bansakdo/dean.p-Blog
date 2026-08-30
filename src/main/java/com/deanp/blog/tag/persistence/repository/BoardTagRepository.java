package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.BoardTag;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.tag.persistence.entity.BoardTagId;

public interface BoardTagRepository extends JpaRepository<BoardTag, BoardTagId> {
}
