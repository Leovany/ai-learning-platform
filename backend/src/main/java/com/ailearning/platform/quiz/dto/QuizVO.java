package com.ailearning.platform.quiz.dto;

import com.ailearning.platform.quiz.Quiz;
import com.ailearning.platform.quiz.QuizStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuizVO {

    private Long id;
    private Long documentId;
    private String documentName;
    private String title;
    private Integer questionCount;
    private QuizStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private Integer progress;
    private LocalDateTime estimatedCompletionTime;
    private List<QuestionVO> questions;
}
