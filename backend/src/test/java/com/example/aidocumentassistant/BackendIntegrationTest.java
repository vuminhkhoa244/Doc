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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID() + "@example.com";
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    @Test
    void registerLoginAndMeReturnUser() throws Exception {
        String email = uniqueEmail("me-test");
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        assertThat(loginBody.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void renameAndDownloadDocument() throws Exception {
        String token = registerAndGetToken(uniqueEmail("docs-test"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Annual leave is 15 days per year.".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult upload = mockMvc.perform(multipart("/api/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(upload.getResponse().getContentAsString());
        long documentId = uploaded.get("id").asLong();

        MvcResult rename = mockMvc.perform(patch("/api/documents/" + documentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"renamed-notes.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("renamed-notes.txt"))
                .andReturn();
        JsonNode renamed = objectMapper.readTree(rename.getResponse().getContentAsString());
        assertThat(renamed.get("filename").asText()).isEqualTo("renamed-notes.txt");

        MvcResult download = mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("renamed-notes.txt");
        assertThat(download.getResponse().getContentAsString()).contains("Annual leave is 15 days");
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatFallsBackWhenAiServiceDown() throws Exception {
        String token = registerAndGetToken(uniqueEmail("chat-fallback"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "handbook.txt", "text/plain",
                "Employees receive 15 days of annual leave after probation. Unused leave may be carried over.".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MvcResult chat = mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many days of annual leave?\",\"tool\":\"rag\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(chat.getResponse().getContentAsString());
        assertThat(body.get("answer").asText()).isNotBlank();
        assertThat(body.get("toolUsed").asText()).isIn("rag", "rag-fallback");
    }
}
