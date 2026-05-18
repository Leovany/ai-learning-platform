package com.ailearning.platform.quiz.llm;

/**
 * LLM 调用结果，含实际使用的提供商与模型（故障转移后为最终成功的一方）。
 */
public record LlmChatResult(String content, String providerId, String model) {}
