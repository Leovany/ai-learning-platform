package com.ailearning.platform.quiz;

import com.ailearning.platform.common.Result;
import com.ailearning.platform.config.AppProperties;
import com.ailearning.platform.quiz.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final AppProperties appProperties;

    @GetMapping("/config")
    public Result<QuizConfigVO> getConfig() {
        QuizConfigVO config = QuizConfigVO.builder()
                .defaultCount(appProperties.getQuiz().getDefaultCount())
                .maxCount(appProperties.getQuiz().getMaxCount())
                .build();
        return Result.ok(config);
    }

    @PostMapping("/generate")
    public Result<QuizVO> generate(@Valid @RequestBody GenerateQuizRequest request) {
        return Result.ok(quizService.generate(request));
    }

    @GetMapping
    public Result<List<QuizVO>> list() {
        return Result.ok(quizService.listAll());
    }

    @GetMapping("/{id}")
    public Result<QuizVO> get(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeAnswers) {
        return Result.ok(quizService.getById(id, includeAnswers));
    }

    @GetMapping("/{id}/status")
    public Result<QuizVO> status(@PathVariable Long id) {
        return Result.ok(quizService.getStatus(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        quizService.cancel(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quizService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    public Result<SubmitQuizResultVO> submit(
            @PathVariable Long id,
            @Valid @RequestBody SubmitQuizRequest request) {
        return Result.ok(quizService.submit(id, request));
    }

    @GetMapping("/{id}/attempts")
    public Result<List<QuizAttemptVO>> attempts(@PathVariable Long id) {
        return Result.ok(quizService.listAttempts(id));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        String content = quizService.exportQuiz(id);
        QuizVO quiz = quizService.getStatus(id);
        String filename = quiz.getTitle().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\-_\\.]", "_") + ".md";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_MARKDOWN);
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
