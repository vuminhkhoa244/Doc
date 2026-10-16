package com.example.aidocumentassistant;

import com.example.aidocumentassistant.document.OcrEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.ai.base-url=http://localhost:1")
@AutoConfigureMockMvc
class UploadImageDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcrEngine ocrEngine;

    private static MockMultipartFile pngUpload(String filename) throws Exception {
        BufferedImage image = new BufferedImage(320, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 320, 80);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", filename, "image/png", bytes.toByteArray());
    }

    private String registerAndGetToken(String prefix) throws Exception {
        MvcResult register = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + prefix + "-" + UUID.randomUUID()
                                + "@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(register.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    @Test
    void imageUploadRunsOcrAndStoresChunks() throws Exception {
        when(ocrEngine.enabled()).thenReturn(true);
        when(ocrEngine.recognize(any())).thenReturn(
                "Company policy grants 20 days of paid annual leave to every employee."
        );
        String token = registerAndGetToken("image-ocr");

        MvcResult upload = mockMvc.perform(multipart("/api/documents")
                        .file(pngUpload("policy-scan.png"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn();
        long documentId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/documents/" + documentId + "/chunks")
                        .param("page", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value(org.hamcrest.Matchers.containsString("20 days of paid annual leave")));
    }

    @Test
    void imageUploadWithOcrDisabledMarksFailedWithClearError() throws Exception {
        when(ocrEngine.enabled()).thenReturn(false);
        String token = registerAndGetToken("image-noocr");

        MvcResult upload = mockMvc.perform(multipart("/api/documents")
                        .file(pngUpload("scan.png"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(upload.getResponse().getContentAsString());

        assertThat(body.get("status").asText()).isEqualTo("failed");
        assertThat(body.get("errorMessage").asText()).contains("Tesseract OCR");
    }

    @Test
    void rejectsUnsupportedArchiveButAcceptsImageContentType() throws Exception {
        String token = registerAndGetToken("image-reject");
        MockMultipartFile archive = new MockMultipartFile(
                "file", "files.zip", "application/zip", new byte[]{'P', 'K'}
        );
        mockMvc.perform(multipart("/api/documents").file(archive)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
