package com.example.aidocumentassistant.auth;

import com.example.aidocumentassistant.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.base-url=http://localhost:1",
        "app.google.client-id=test-client-id",
        "spring.datasource.url=jdbc:h2:mem:google-login;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class GoogleLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    private GoogleIdToken.Payload payload(String email, String sub) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setSubject(sub);
        payload.set("name", "Test User");
        payload.set("picture", "https://example.com/avatar.png");
        return payload;
    }

    private String registerPasswordUser(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("user").get("email").asText()).isEqualTo(email);
        return email;
    }

    @Test
    void createsNewUserFromValidGoogleToken() throws Exception {
        String email = "google-" + UUID.randomUUID() + "@example.com";
        when(googleTokenVerifier.enabled()).thenReturn(true);
        when(googleTokenVerifier.verify("valid-token")).thenReturn(payload(email, "g-123"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Test User"));
    }

    @Test
    void linksGoogleToExistingPasswordAccount() throws Exception {
        String email = registerPasswordUser("link");
        AppUser existing = users.findByEmailIgnoreCase(email).orElseThrow();

        when(googleTokenVerifier.enabled()).thenReturn(true);
        when(googleTokenVerifier.verify("valid-token")).thenReturn(payload(email, "g-456"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existing.getId()));

        AppUser reloaded = users.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getGoogleId()).isEqualTo("g-456");
    }

    @Test
    void returns401ForInvalidToken() throws Exception {
        when(googleTokenVerifier.enabled()).thenReturn(true);
        when(googleTokenVerifier.verify("bad-token")).thenThrow(new IllegalArgumentException("bad"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns503WhenGoogleNotConfigured() throws Exception {
        when(googleTokenVerifier.enabled()).thenReturn(false);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"any\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returns400ForBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
