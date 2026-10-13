package com.example.aidocumentassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.ai.base-url=http://localhost:1")
@AutoConfigureMockMvc
class UploadWithoutAiServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadWithoutAiServiceMarksFailedButStoresDocument() throws Exception {
        String email = "fallback-test-" + UUID.randomUUID() + "@example.com";
        MvcResult register = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper
                .readTree(register.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain",
                "Employees receive 15 days of annual leave.".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult upload = mockMvc.perform(multipart("/api/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(upload.getResponse().getContentAsString());
        assertThat(uploaded.get("status").asText()).isEqualTo("indexed");
        assertThat(uploaded.get("chunks").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(uploaded.get("errorMessage").asText()).contains("AI service unavailable");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
