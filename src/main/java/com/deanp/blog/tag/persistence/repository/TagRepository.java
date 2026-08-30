package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
}
