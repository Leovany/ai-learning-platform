package com.ailearning.platform.quiz.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateQuizRequest {

    @NotNull(message = "documentId 不能为空")
    private Long documentId;

    private Integer questionCount;

    /** 难度：easy | medium | hard，默认 medium */
    private String difficulty;
}
