package com.example.aidocumentassistant.ai;

import com.example.aidocumentassistant.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AiClientLiveRoundTripTest {

    private static boolean aiServiceReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 8000), 500);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static AiClient client() {
        AppProperties properties = new AppProperties(
                null,
                null,
                null,
                new AppProperties.Ai("http://localhost:8000"),
                new AppProperties.WebSearch("http://localhost:8888", 8, 5),
                null
        );
        return new AiClient(properties);
    }

    @Test
    void generateRoundTripsAgainstRunningService() {
        assumeTrue(aiServiceReachable(), "ai-service not running on localhost:8000 — skipping live round trip");

        AiClient.GenerateResponse response = client().generate(new AiClient.GenerateRequest(
                "What is Docker?",
                "[1] overview\nURL: https://docs.docker.com\nDocker is an open platform for developing and running applications in containers.",
                List.of()
        ));

        assertFalse(response.answer().isBlank());
    }

    @Test
    void queryRoundTripsAgainstRunningService() {
        assumeTrue(aiServiceReachable(), "ai-service not running on localhost:8000 — skipping live round trip");

        AiClient.QueryResponse response = client().query(new AiClient.QueryRequest(
                "How many days of annual leave?",
                List.of(),
                List.of()
        ));

        assertFalse(response.answer().isBlank());
    }
}
