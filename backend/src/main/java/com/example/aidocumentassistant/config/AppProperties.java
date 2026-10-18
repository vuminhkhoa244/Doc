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
        Ocr ocr,
        Images images,
        Google google
) {
    public AppProperties {
        jwt = jwt == null ? new Jwt("change-this-development-secret-change-this-development-secret", 120, 14) : jwt;
        cors = cors == null ? new Cors(List.of("http://localhost:5173", "http://127.0.0.1:5173")) : cors;
        storage = storage == null ? new Storage(Path.of("./data/uploads")) : storage;
        ai = ai == null ? new Ai("http://localhost:8000") : ai;
        webSearch = webSearch == null ? new WebSearch("http://localhost:8888", 8, 5) : webSearch;
        ocr = ocr == null ? new Ocr(false, "/nonexistent-tessdata", "vie+eng", 300, 50, 10) : ocr;
        images = images == null ? new Images(true, 64, 30) : images;
        google = google == null ? new Google("") : google;
    }

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

    public record Images(boolean enabled, int minPixelSize, int maxPerDocument) {
    }

    public record Google(String clientId) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
