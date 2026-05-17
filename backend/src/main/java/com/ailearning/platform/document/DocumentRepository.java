package com.ailearning.platform.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<LearningDocument, Long> {

    List<LearningDocument> findAllByOrderByCreatedAtDesc();
}
