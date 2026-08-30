package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.VisitorDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VisitorDailySummaryRepository extends JpaRepository<VisitorDailySummary, UUID> {
}
