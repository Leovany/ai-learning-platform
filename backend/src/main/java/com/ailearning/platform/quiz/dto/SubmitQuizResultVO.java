package com.ailearning.platform.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubmitQuizResultVO {

    private Long attemptId;
    private Long quizId;
    private Long documentId;
    private String documentName;
    private String title;
    private Integer score;
    private Integer total;
    private List<QuestionVO> questions;
}
