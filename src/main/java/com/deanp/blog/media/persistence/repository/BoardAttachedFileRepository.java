package com.deanp.blog.media.persistence.repository;

import com.deanp.blog.media.persistence.entity.BoardAttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.media.persistence.entity.BoardAttachedFileId;

public interface BoardAttachedFileRepository extends JpaRepository<BoardAttachedFile, BoardAttachedFileId> {
}
