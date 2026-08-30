package com.deanp.blog.publishing.persistence.repository;

import com.deanp.blog.publishing.persistence.entity.PublishHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PublishHistoryRepository extends JpaRepository<PublishHistory, UUID> {
}
