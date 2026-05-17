package com.ailearning.platform.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByQuizIdOrderBySortOrderAsc(Long quizId);

    void deleteByQuizId(Long quizId);
}
