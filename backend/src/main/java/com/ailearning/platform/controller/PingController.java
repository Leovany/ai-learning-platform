package com.ailearning.platform.controller;

import com.ailearning.platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("status", "ok", "service", "ai-learning-platform"));
    }
}
