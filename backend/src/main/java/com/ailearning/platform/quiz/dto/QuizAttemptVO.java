package com.ailearning.platform.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuizAttemptVO {

    private Long id;
    private Long quizId;
    private Integer score;
    private Integer total;
    private LocalDateTime submittedAt;
}
