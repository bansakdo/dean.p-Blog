package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VisitorEventRepository extends JpaRepository<VisitorEvent, UUID> {
}
