package com.ailearning.platform.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "quiz")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private String title;

    private Integer questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizStatus status = QuizStatus.PENDING;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Integer progress;

    private LocalDateTime estimatedCompletionTime;

    private String taskToken;

    /** 实际生成考题使用的大模型提供商，如 zhipu / deepseek / qwen */
    private String llmProvider;

    /** 实际生成考题使用的模型名，如 glm-4.7-flash */
    private String llmModel;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (progress == null) {
            progress = 0;
        }
    }
}
