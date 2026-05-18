package com.ailearning.platform.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findAllByOrderByCreatedAtDesc();

    List<Quiz> findByStatusAndCreatedAtBefore(QuizStatus status, LocalDateTime before);

    List<Quiz> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    void deleteByDocumentId(Long documentId);
}
