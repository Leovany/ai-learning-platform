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
        private String apiBase = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
    }

    @Data
    public static class Quiz {
        private int defaultCount = 10;
        private int maxCount = 30;
    }
}
