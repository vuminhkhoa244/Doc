package com.example.aidocumentassistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated liveness probe used by Render's health checks (and useful
 * for local smoke tests). Returns 200 as long as the JVM is serving requests;
 * database reachability is covered by request-time error handling.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
