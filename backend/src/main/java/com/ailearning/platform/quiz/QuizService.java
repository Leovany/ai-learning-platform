package com.ailearning.platform.quiz;

import com.ailearning.platform.common.BusinessException;
import com.ailearning.platform.config.AppProperties;
import com.ailearning.platform.document.*;
import com.ailearning.platform.quiz.dto.*;
import com.ailearning.platform.quiz.llm.LlmClient;
import com.ailearning.platform.quiz.llm.LlmGeneratedQuestion;
import com.ailearning.platform.quiz.llm.LlmQuizParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int TEXT_MAX_LENGTH = 12000;
    private static final int MAX_LLM_RETRIES = 3;

    @Autowired
    @Lazy
    private QuizService self;

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final DocumentRepository documentRepository;
    private final LlmClient llmClient;
    private final LlmQuizParser llmQuizParser;
    private final DocumentPageService documentPageService;
    private final PdfPageLocator pdfPageLocator;
    private final AppProperties appProperties;

    private final Map<Long, Boolean> cancelTokens = new ConcurrentHashMap<>();

    @Transactional
    public QuizVO generate(GenerateQuizRequest request) {
        LearningDocument doc = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        if (doc.getStatus() != DocumentStatus.PARSED) {
            throw BusinessException.badRequest("文档尚未解析完成，无法生成考题");
        }
        if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
            throw BusinessException.badRequest("文档无可用文本，无法生成考题（可能是扫描版 PDF）");
        }

        int count = resolveQuestionCount(request.getQuestionCount());
        validateDifficulty(request.getDifficulty());

        Quiz quiz = new Quiz();
        quiz.setDocumentId(doc.getId());
        quiz.setTitle(doc.getFileName() + " - 练习题");
        quiz.setQuestionCount(count);
        quiz.setStatus(QuizStatus.PENDING);
        quiz.setProgress(0);
        quiz.setTaskToken(UUID.randomUUID().toString());
        quiz = quizRepository.save(quiz);

        cancelTokens.put(quiz.getId(), false);

        self.asyncGenerateQuiz(quiz.getId(), request.getQuestionCount(), request.getDifficulty());

        return toSummaryVO(quiz, doc.getFileName());
    }

    @Async("taskExecutor")
    public void asyncGenerateQuiz(Long quizId, Integer questionCount, String difficulty) {
        log.info("Async task started for quizId: {}", quizId);
        try {
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz == null) {
                return;
            }

            updateProgress(quizId, 5, "开始处理文档...");
            quiz.setStatus(QuizStatus.GENERATING);
            quiz.setEstimatedCompletionTime(LocalDateTime.now().plusMinutes(5));
            quizRepository.save(quiz);

            if (isCancelled(quizId)) {
                markAsCancelled(quizId);
                return;
            }

            LearningDocument doc = documentRepository.findById(quiz.getDocumentId()).orElse(null);
            if (doc == null) {
                updateQuizFailed(quizId, "文档不存在");
                return;
            }

            updateProgress(quizId, 10, "解析文档内容...");

            if (isCancelled(quizId)) {
                markAsCancelled(quizId);
                return;
            }

            int count = resolveQuestionCount(questionCount);

            updateProgress(quizId, 20, "准备调用大模型...");

            if (isCancelled(quizId)) {
                markAsCancelled(quizId);
                return;
            }

            updateProgress(quizId, 30, "调用大模型生成题目...");

            List<LlmGeneratedQuestion> generated = callLlmWithRetry(doc, count, difficulty);

            if (isCancelled(quizId)) {
                markAsCancelled(quizId);
                return;
            }

            updateProgress(quizId, 70, "保存题目数据...");

            saveQuestions(quizId, doc, generated);

            if (isCancelled(quizId)) {
                markAsCancelled(quizId);
                return;
            }

            updateProgress(quizId, 90, "完成任务...");

            quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz != null) {
                quiz.setQuestionCount(generated.size());
                quiz.setStatus(QuizStatus.READY);
                quiz.setProgress(100);
                quiz.setEstimatedCompletionTime(LocalDateTime.now());
                quizRepository.save(quiz);
            }

            cancelTokens.remove(quizId);
            log.info("Quiz {} generated successfully with {} questions", quizId, generated.size());

        } catch (BusinessException e) {
            updateQuizFailed(quizId, e.getMessage());
        } catch (Exception e) {
            log.error("Generate quiz {} failed", quizId, e);
            updateQuizFailed(quizId, "生成失败: " + e.getMessage());
        } finally {
            cancelTokens.remove(quizId);
        }
    }

    private void updateProgress(Long quizId, int progress, String message) {
        try {
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz != null) {
                quiz.setProgress(progress);
                quiz.setEstimatedCompletionTime(LocalDateTime.now().plusMinutes(2));
                quizRepository.save(quiz);
            }
        } catch (Exception e) {
            log.warn("Failed to update progress for quiz {}", quizId, e);
        }
    }

    private boolean isCancelled(Long quizId) {
        return Boolean.TRUE.equals(cancelTokens.get(quizId));
    }

    private void markAsCancelled(Long quizId) {
        try {
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz != null) {
                quiz.setStatus(QuizStatus.CANCELLED);
                quiz.setErrorMessage("任务已取消");
                quizRepository.save(quiz);
            }
        } catch (Exception e) {
            log.warn("Failed to mark quiz {} as cancelled", quizId, e);
        }
    }

    @Transactional
    public void cancel(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> BusinessException.notFound("任务不存在"));
        if (quiz.getStatus() != QuizStatus.PENDING && quiz.getStatus() != QuizStatus.GENERATING) {
            throw BusinessException.badRequest("只有等待中或生成中的任务才能取消");
        }
        cancelTokens.put(quizId, true);
        quiz.setStatus(QuizStatus.CANCELLED);
        quiz.setErrorMessage("任务已取消");
        quizRepository.save(quiz);
    }

    private void updateQuizFailed(Long quizId, String errorMessage) {
        try {
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz != null) {
                quiz.setStatus(QuizStatus.FAILED);
                quiz.setErrorMessage(errorMessage);
                quiz.setProgress(0);
                quizRepository.save(quiz);
            }
        } catch (Exception e) {
            log.error("Failed to update quiz {} status to FAILED", quizId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<QuizVO> listAll() {
        Map<Long, String> docNames = documentRepository.findAll().stream()
                .collect(Collectors.toMap(LearningDocument::getId, LearningDocument::getFileName));
        return quizRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(q -> toSummaryVO(q, docNames.get(q.getDocumentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizVO getById(Long id, boolean includeAnswers) {
        Quiz quiz = findQuizOrThrow(id);
        if (quiz.getStatus() != QuizStatus.READY) {
            throw BusinessException.badRequest(
                    quiz.getStatus() == QuizStatus.GENERATING ? "试卷正在生成中，请稍候..." : "试卷不可用"
            );
        }
        return toDetailVO(quiz, includeAnswers);
    }

    @Transactional(readOnly = true)
    public QuizVO getStatus(Long id) {
        Quiz quiz = findQuizOrThrow(id);
        String docName = documentRepository.findById(quiz.getDocumentId())
                .map(LearningDocument::getFileName)
                .orElse("");
        return toSummaryVO(quiz, docName);
    }

    @Transactional
    public void delete(Long id) {
        Quiz quiz = findQuizOrThrow(id);
        cancelTokens.put(id, true);
        deleteQuizData(quiz.getId());
        quizRepository.delete(quiz);
        cancelTokens.remove(id);
    }

    @Transactional
    public void deleteByDocumentId(Long documentId) {
        List<Quiz> quizzes = quizRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        for (Quiz quiz : quizzes) {
            cancelTokens.put(quiz.getId(), true);
            deleteQuizData(quiz.getId());
            quizRepository.delete(quiz);
            cancelTokens.remove(quiz.getId());
        }
    }

    @Transactional
    public SubmitQuizResultVO submit(Long quizId, SubmitQuizRequest request) {
        Quiz quiz = findQuizOrThrow(quizId);
        if (quiz.getStatus() != QuizStatus.READY) {
            throw BusinessException.badRequest("试卷不可提交");
        }
        List<Question> questions = questionRepository.findByQuizIdOrderBySortOrderAsc(quizId);
        if (questions.isEmpty()) {
            throw BusinessException.badRequest("试卷没有题目");
        }

        Map<Long, String> answerMap = request.getAnswers().stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitQuizRequest.AnswerItem::getQuestionId,
                        a -> a.getUserAnswer() == null ? "" : a.getUserAnswer().trim().toUpperCase(Locale.ROOT),
                        (a, b) -> b
                ));

        int score = 0;
        List<QuestionVO> resultQuestions = new ArrayList<>();
        List<AnswerRecord> records = new ArrayList<>();

        LearningDocument doc = documentRepository.findById(quiz.getDocumentId()).orElse(null);
        List<PageText> pages = doc != null ? documentPageService.loadPages(doc) : List.of();

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuizId(quizId);
        attempt.setTotal(questions.size());
        attempt = quizAttemptRepository.save(attempt);

        for (Question q : questions) {
            String userAnswer = answerMap.getOrDefault(q.getId(), "");
            boolean correct = q.getCorrectAnswer().equalsIgnoreCase(userAnswer);
            if (correct) {
                score++;
            }
            AnswerRecord record = new AnswerRecord();
            record.setAttemptId(attempt.getId());
            record.setQuestionId(q.getId());
            record.setUserAnswer(userAnswer.isEmpty() ? null : userAnswer);
            record.setIsCorrect(correct);
            records.add(record);
            Integer pageCount = doc != null ? doc.getPageCount() : null;
            resultQuestions.add(buildResultQuestionVO(
                    q, pages, userAnswer.isEmpty() ? null : userAnswer, correct, pageCount));
        }
        answerRecordRepository.saveAll(records);

        attempt.setScore(score);
        attempt = quizAttemptRepository.save(attempt);

        String docName = doc != null ? doc.getFileName() : "";
        return SubmitQuizResultVO.builder()
                .attemptId(attempt.getId())
                .quizId(quizId)
                .documentId(quiz.getDocumentId())
                .documentName(docName)
                .title(quiz.getTitle())
                .score(score)
                .total(questions.size())
                .questions(resultQuestions)
                .build();
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptVO> listAttempts(Long quizId) {
        findQuizOrThrow(quizId);
        return quizAttemptRepository.findByQuizIdOrderBySubmittedAtDesc(quizId).stream()
                .map(a -> QuizAttemptVO.builder()
                        .id(a.getId())
                        .quizId(a.getQuizId())
                        .score(a.getScore())
                        .total(a.getTotal())
                        .submittedAt(a.getSubmittedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportQuiz(Long quizId) {
        Quiz quiz = findQuizOrThrow(quizId);
        if (quiz.getStatus() != QuizStatus.READY) {
            throw BusinessException.badRequest("试卷尚未生成完成");
        }
        List<Question> questions = questionRepository.findByQuizIdOrderBySortOrderAsc(quizId);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(quiz.getTitle()).append("\n\n");
        sb.append("生成时间: ").append(quiz.getCreatedAt()).append("\n");
        sb.append("题目数量: ").append(questions.size()).append("\n\n");

        int index = 1;
        for (Question q : questions) {
            sb.append("## ").append(index++).append(". ").append(q.getStem()).append("\n\n");
            sb.append("A. ").append(q.getOptionA()).append("\n");
            sb.append("B. ").append(q.getOptionB()).append("\n");
            sb.append("C. ").append(q.getOptionC()).append("\n");
            sb.append("D. ").append(q.getOptionD()).append("\n\n");
            sb.append("答案: ").append(q.getCorrectAnswer()).append("\n");
            if (q.getExplanation() != null) {
                sb.append("解析: ").append(q.getExplanation()).append("\n");
            }
            sb.append("\n---\n\n");
        }

        return sb.toString();
    }

    private List<LlmGeneratedQuestion> callLlmWithRetry(LearningDocument doc, int count, String difficulty) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(doc, count, difficulty);
        BusinessException lastError = null;
        for (int i = 0; i < MAX_LLM_RETRIES; i++) {
            try {
                String content = llmClient.chat(systemPrompt, userPrompt);
                return llmQuizParser.parse(content, count);
            } catch (BusinessException e) {
                lastError = e;
                log.warn("LLM attempt {} failed: {}", i + 1, e.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw BusinessException.serviceUnavailable("生成考题失败");
    }

    private void saveQuestions(Long quizId, LearningDocument doc, List<LlmGeneratedQuestion> generated) {
        List<PageText> pages = documentPageService.loadPages(doc);
        int maxPage = doc.getPageCount() != null ? doc.getPageCount() : pages.size();
        int order = 1;
        for (LlmGeneratedQuestion g : generated) {
            Question q = new Question();
            q.setQuizId(quizId);
            q.setSortOrder(order++);
            q.setStem(g.getStem());
            q.setOptionA(g.getOptions().get("A"));
            q.setOptionB(g.getOptions().get("B"));
            q.setOptionC(g.getOptions().get("C"));
            q.setOptionD(g.getOptions().get("D"));
            q.setCorrectAnswer(g.getCorrectAnswer().toUpperCase(Locale.ROOT));
            q.setExplanation(g.getExplanation());
            q.setSourceQuote(g.getSourceQuote() != null && !g.getSourceQuote().isBlank()
                    ? g.getSourceQuote() : null);
            applyPageLocation(q, pages, g, maxPage);
            questionRepository.save(q);
        }
    }

    private void applyPageLocation(Question q, List<PageText> pages, LlmGeneratedQuestion g, int maxPage) {
        PageMatch match = null;
        if (g.getSourcePage() != null && g.getSourcePage() > 0 && g.getSourcePage() <= maxPage) {
            int pageNum = g.getSourcePage();
            List<PageText> single = pages.stream().filter(p -> p.getPage() == pageNum).toList();
            match = pdfPageLocator.locateMatch(single, g.getSourceQuote(), g.getExplanation());
            if (match == null) {
                match = PageMatch.builder().page(pageNum).highlightStart(-1).highlightEnd(-1).build();
            }
        } else {
            match = pdfPageLocator.locateMatch(pages, g.getSourceQuote(), g.getExplanation());
        }
        if (match != null && match.getPage() > 0) {
            q.setSourcePage(match.getPage());
            if ((q.getSourceQuote() == null || q.getSourceQuote().isBlank())
                    && match.getMatchedSnippet() != null) {
                q.setSourceQuote(match.getMatchedSnippet());
            }
            if (match.hasHighlight()) {
                q.setSourceHighlightStart(match.getHighlightStart());
                q.setSourceHighlightEnd(match.getHighlightEnd());
            }
        }
    }

    private QuestionVO buildResultQuestionVO(
            Question q,
            List<PageText> pages,
            String userAnswer,
            boolean correct,
            Integer documentPageCount) {
        Integer page = q.getSourcePage();
        Integer hStart = q.getSourceHighlightStart();
        Integer hEnd = q.getSourceHighlightEnd();
        String quote = q.getSourceQuote();

        if ((page == null || page <= 0) && pages != null && !pages.isEmpty()) {
            PageMatch match = pdfPageLocator.locateMatch(pages, quote, q.getExplanation());
            if (match != null) {
                page = match.getPage();
                if (match.hasHighlight()) {
                    hStart = match.getHighlightStart();
                    hEnd = match.getHighlightEnd();
                }
                if ((quote == null || quote.isBlank()) && match.getMatchedSnippet() != null) {
                    quote = match.getMatchedSnippet();
                }
            }
        }

        String excerpt = quote;
        if ((excerpt == null || excerpt.isBlank()) && page != null && page > 0) {
            excerpt = pdfPageLocator.excerptOnPage(pages, page, 300);
        }

        return QuestionVO.builder()
                .id(q.getId())
                .sortOrder(q.getSortOrder())
                .stem(q.getStem())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .correctAnswer(q.getCorrectAnswer())
                .explanation(q.getExplanation())
                .sourcePage(page)
                .sourceQuote(quote)
                .sourceHighlightStart(hStart)
                .sourceHighlightEnd(hEnd)
                .pdfExcerpt(excerpt)
                .documentPageCount(documentPageCount)
                .userAnswer(userAnswer)
                .isCorrect(correct)
                .build();
    }

    private void deleteQuizData(Long quizId) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizIdOrderBySubmittedAtDesc(quizId);
        List<Long> attemptIds = attempts.stream().map(QuizAttempt::getId).toList();
        if (!attemptIds.isEmpty()) {
            answerRecordRepository.deleteByAttemptIdIn(attemptIds);
            quizAttemptRepository.deleteAll(attempts);
        }
        questionRepository.deleteByQuizId(quizId);
    }

    private QuizVO toDetailVO(Quiz quiz, boolean includeAnswers) {
        String docName = documentRepository.findById(quiz.getDocumentId())
                .map(LearningDocument::getFileName)
                .orElse("");
        List<Question> questions = questionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId());
        List<QuestionVO> questionVOs = questions.stream()
                .map(q -> includeAnswers ? QuestionVO.withAnswer(q, null, null) : QuestionVO.forExam(q))
                .toList();
        return QuizVO.builder()
                .id(quiz.getId())
                .documentId(quiz.getDocumentId())
                .documentName(docName)
                .title(quiz.getTitle())
                .questionCount(quiz.getQuestionCount())
                .status(quiz.getStatus())
                .errorMessage(quiz.getErrorMessage())
                .createdAt(quiz.getCreatedAt())
                .progress(quiz.getProgress())
                .estimatedCompletionTime(quiz.getEstimatedCompletionTime())
                .questions(questionVOs)
                .build();
    }

    private QuizVO toSummaryVO(Quiz quiz, String docName) {
        return QuizVO.builder()
                .id(quiz.getId())
                .documentId(quiz.getDocumentId())
                .documentName(docName)
                .title(quiz.getTitle())
                .questionCount(quiz.getQuestionCount())
                .status(quiz.getStatus())
                .errorMessage(quiz.getErrorMessage())
                .createdAt(quiz.getCreatedAt())
                .progress(quiz.getProgress())
                .estimatedCompletionTime(quiz.getEstimatedCompletionTime())
                .build();
    }

    private Quiz findQuizOrThrow(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("试卷不存在"));
    }

    private void validateDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return;
        }
        String d = difficulty.toLowerCase(Locale.ROOT);
        if (!Set.of("easy", "medium", "hard").contains(d)) {
            throw BusinessException.badRequest("难度只能是 easy、medium 或 hard");
        }
    }

    private int resolveQuestionCount(Integer requested) {
        int count = requested == null ? appProperties.getQuiz().getDefaultCount() : requested;
        int max = appProperties.getQuiz().getMaxCount();
        if (count < 1) {
            throw BusinessException.badRequest("题目数量至少为 1");
        }
        if (count > max) {
            throw BusinessException.badRequest("题目数量不能超过 " + max);
        }
        return count;
    }

    private String buildSystemPrompt() {
        return """
                你是一位专业的出题老师。根据用户提供的学习材料，生成单项选择题。
                必须只输出合法 JSON，不要包含 markdown 或其它说明文字。
                JSON 格式如下：
                {
                  "questions": [
                    {
                      "stem": "题干",
                      "options": { "A": "选项A", "B": "选项B", "C": "选项C", "D": "选项D" },
                      "correctAnswer": "A",
                      "explanation": "答案解析",
                      "sourcePage": 1,
                      "sourceQuote": "支持答案的原文摘录（20-80字）"
                    }
                  ]
                }
                要求：题目不重复；每题 4 个选项且只有一个正确答案；correctAnswer 只能是 A/B/C/D；
                sourcePage 为依据原文所在 PDF 页码（整数，从 1 开始）；sourceQuote 为该行原文摘录。
                """;
    }

    private String buildUserPrompt(LearningDocument doc, int count, String difficulty) {
        List<PageText> pages = documentPageService.loadPages(doc);
        String body;
        if (!pages.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int total = 0;
            for (PageText p : pages) {
                String block = "--- PDF第" + p.getPage() + "页 ---\n" + p.getText();
                if (total + block.length() > TEXT_MAX_LENGTH) {
                    int remain = TEXT_MAX_LENGTH - total;
                    if (remain > 0) {
                        sb.append(block, 0, Math.min(block.length(), remain));
                    }
                    sb.append("\n…（后续内容已截断）");
                    break;
                }
                sb.append(block).append("\n\n");
                total += block.length() + 2;
            }
            body = sb.toString();
        } else {
            body = doc.getExtractedText();
            if (body.length() > TEXT_MAX_LENGTH) {
                body = body.substring(0, TEXT_MAX_LENGTH) + "\n…（后续内容已截断）";
            }
        }
        String diffHint = resolveDifficultyHint(difficulty);
        return "文档名称：" + doc.getFileName()
                + "\n难度要求：" + diffHint
                + "\n请根据以下学习材料生成 " + count + " 道选择题：\n\n" + body;
    }

    private String resolveDifficultyHint(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "中等（概念理解与应用）";
        }
        return switch (difficulty.toLowerCase(Locale.ROOT)) {
            case "easy" -> "简单（基础概念与记忆）";
            case "hard" -> "困难（综合分析与易混淆点辨析）";
            default -> "中等（概念理解与应用）";
        };
    }
}
