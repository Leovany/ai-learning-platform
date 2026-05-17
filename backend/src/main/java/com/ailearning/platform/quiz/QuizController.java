package com.ailearning.platform.quiz;

import com.ailearning.platform.common.Result;
import com.ailearning.platform.quiz.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

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
}
