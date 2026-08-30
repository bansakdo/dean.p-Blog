package com.deanp.blog.media.persistence.repository;

import com.deanp.blog.media.persistence.entity.AttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AttachedFileRepository extends JpaRepository<AttachedFile, UUID> {
}
