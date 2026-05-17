package com.ailearning.platform.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class DataDirInitializer {

    private final AppProperties appProperties;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Path.of(appProperties.getDataDir(), "db"));
        Files.createDirectories(Path.of(appProperties.getUploadDir()));
    }
}
