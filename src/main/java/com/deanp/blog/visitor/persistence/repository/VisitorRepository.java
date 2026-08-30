package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {
}
