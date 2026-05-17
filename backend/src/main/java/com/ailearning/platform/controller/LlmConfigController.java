package com.ailearning.platform.controller;

import com.ailearning.platform.common.Result;
import com.ailearning.platform.quiz.llm.LlmConfigResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigResolver llmConfigResolver;

    @GetMapping("/config")
    public Result<Map<String, String>> config() {
        LlmConfigResolver.ResolvedLlm llm = llmConfigResolver.resolve();
        return Result.ok(Map.of(
                "provider", llm.providerId(),
                "model", llm.model(),
                "apiBase", llm.apiBase()
        ));
    }
}
