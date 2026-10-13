package com.example.aidocumentassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Ai ai,
        WebSearch webSearch,
        Ocr ocr
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
        public Duration accessTokenTtl() {
            return Duration.ofMinutes(accessTokenMinutes);
        }

        public Duration refreshTokenTtl() {
            return Duration.ofDays(refreshTokenDays);
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(Path uploadDir) {
    }

    public record Ai(String baseUrl) {
    }

    public record WebSearch(String baseUrl, int timeoutSeconds, int maxResults) {
    }

    public record Ocr(boolean enabled, String tessdataDir, String language, int dpi, int maxPages, int sparseMinChars) {
    }
}
