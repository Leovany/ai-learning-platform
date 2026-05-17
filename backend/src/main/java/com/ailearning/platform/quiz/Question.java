package com.ailearning.platform.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quizId;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionA;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionB;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionC;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionD;

    @Column(nullable = false, length = 1)
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** 答案依据在 PDF 中的页码（1-based） */
    private Integer sourcePage;

    /** 原文摘录，用于定位与展示 */
    @Column(columnDefinition = "TEXT")
    private String sourceQuote;

    /** 原文在该页文本中的高亮起始位置 */
    private Integer sourceHighlightStart;

    /** 原文在该页文本中的高亮结束位置 */
    private Integer sourceHighlightEnd;
}
