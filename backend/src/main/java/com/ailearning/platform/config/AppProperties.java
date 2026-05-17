package com.ailearning.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String uploadDir = "./data/uploads";
    private Llm llm = new Llm();
    private Quiz quiz = new Quiz();

    @Data
    public static class Llm {
        /** 当前提供商：zhipu | deepseek | auto（根据 LLM_MODEL 自动推断） */
        private String provider = "auto";
        /** 指定模型名；不填则使用对应提供商的 default-model */
        private String model = "";
        private ProviderConfig zhipu = new ProviderConfig();
        private ProviderConfig deepseek = new ProviderConfig();
    }

    @Data
    public static class ProviderConfig {
        private String apiBase = "";
        private String apiKey = "";
        private String defaultModel = "";
    }

    @Data
    public static class Quiz {
        private int defaultCount = 10;
        private int maxCount = 30;
    }
}
