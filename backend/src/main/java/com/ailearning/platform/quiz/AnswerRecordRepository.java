package com.ailearning.platform.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, Long> {

    List<AnswerRecord> findByAttemptId(Long attemptId);

    void deleteByAttemptIdIn(List<Long> attemptIds);
}
