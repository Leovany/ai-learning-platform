package com.ailearning.platform;

import com.ailearning.platform.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class AiLearningPlatformApplication {

    public static void main(String[] args) {
        String dataDir = System.getenv().getOrDefault("APP_DATA_DIR", "./data");
        try {
            Files.createDirectories(Path.of(dataDir, "db"));
            Files.createDirectories(Path.of(dataDir, "uploads"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SpringApplication.run(AiLearningPlatformApplication.class, args);
    }
}
