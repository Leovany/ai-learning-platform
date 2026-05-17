package com.ailearning.platform.quiz.dto;

import com.ailearning.platform.quiz.Question;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionVO {

    private Long id;
    private Integer sortOrder;
    private String stem;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
    private String explanation;
    private Integer sourcePage;
    private String sourceQuote;
    private Integer sourceHighlightStart;
    private Integer sourceHighlightEnd;
    private String pdfExcerpt;
    private Integer documentPageCount;
    private String userAnswer;
    private Boolean isCorrect;

    public static QuestionVO forExam(Question q) {
        return QuestionVO.builder()
                .id(q.getId())
                .sortOrder(q.getSortOrder())
                .stem(q.getStem())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .build();
    }

    public static QuestionVO withAnswer(Question q, String userAnswer, Boolean isCorrect) {
        return withAnswer(q, userAnswer, isCorrect, null);
    }

    public static QuestionVO withAnswer(Question q, String userAnswer, Boolean isCorrect, String pdfExcerpt) {
        return withAnswer(q, userAnswer, isCorrect, pdfExcerpt, null);
    }

    public static QuestionVO withAnswer(
            Question q,
            String userAnswer,
            Boolean isCorrect,
            String pdfExcerpt,
            Integer documentPageCount) {
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
                .sourcePage(q.getSourcePage())
                .sourceQuote(q.getSourceQuote())
                .sourceHighlightStart(q.getSourceHighlightStart())
                .sourceHighlightEnd(q.getSourceHighlightEnd())
                .pdfExcerpt(pdfExcerpt)
                .documentPageCount(documentPageCount)
                .userAnswer(userAnswer)
                .isCorrect(isCorrect)
                .build();
    }
}
