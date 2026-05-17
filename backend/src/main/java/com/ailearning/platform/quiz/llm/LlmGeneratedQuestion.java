package com.ailearning.platform.quiz.llm;

import lombok.Data;

import java.util.Map;

@Data
public class LlmGeneratedQuestion {

    private String stem;
    private Map<String, String> options;
    private String correctAnswer;
    private String explanation;
    private Integer sourcePage;
    private String sourceQuote;
}
