package com.ailearning.platform.quiz.llm;

import lombok.Data;

import java.util.List;

@Data
public class LlmQuizResponse {

    private List<LlmGeneratedQuestion> questions;
}
