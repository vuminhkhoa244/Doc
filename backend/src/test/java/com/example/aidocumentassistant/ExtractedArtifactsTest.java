package com.example.aidocumentassistant;

import com.example.aidocumentassistant.document.OcrEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
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
class ExtractedArtifactsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcrEngine ocrEngine;

    private static MockMultipartFile docxWithTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Budget appendix.");
            XWPFTable table = document.createTable(3, 2);
            table.getRow(0).getCell(0).setText("Category");
            table.getRow(0).getCell(1).setText("Amount");
            table.getRow(1).getCell(0).setText("Travel");
            table.getRow(1).getCell(1).setText("1200");
            table.getRow(2).getCell(0).setText("Training");
            table.getRow(2).getCell(1).setText("800");
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                document.write(bytes);
                return new MockMultipartFile(
                        "file",
                        "budget.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        bytes.toByteArray()
                );
            }
        }
    }

    private static MockMultipartFile pngUpload(String filename) throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 200, 100);
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
    void docxUploadExtractsTablesAndIndexesTableChunks() throws Exception {
        when(ocrEngine.enabled()).thenReturn(false);
        String token = registerAndGetToken("tables-docx");

        MvcResult upload = mockMvc.perform(multipart("/api/documents")
                        .file(docxWithTable())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long documentId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/documents/" + documentId + "/tables")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowCount").value(2))
                .andExpect(jsonPath("$[0].markdown").value(org.hamcrest.Matchers.containsString("Travel")))
                .andExpect(jsonPath("$[0].markdown").value(org.hamcrest.Matchers.containsString("1200")));

        MvcResult chunks = mockMvc.perform(get("/api/documents/" + documentId + "/chunks")
                        .param("page", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String chunkText = chunks.getResponse().getContentAsString();
        assertThat(chunkText).contains("Table (page 1)");
        assertThat(chunkText).contains("Training");
    }

    @Test
    void imageSearchFindsOcrCaptionAndServesContent() throws Exception {
        when(ocrEngine.enabled()).thenReturn(true);
        when(ocrEngine.recognize(any())).thenReturn("Quarterly revenue chart for 2026");
        String token = registerAndGetToken("img-search");

        mockMvc.perform(multipart("/api/documents")
                        .file(pngUpload("revenue-chart.png"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MvcResult search = mockMvc.perform(get("/api/images/search")
                        .param("q", "revenue")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("revenue-chart.png"))
                .andExpect(jsonPath("$[0].page").value(1))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.containsString("Quarterly revenue")))
                .andReturn();
        long imageId = objectMapper.readTree(search.getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(get("/api/images/" + imageId + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType("image/png"));

        mockMvc.perform(get("/api/images/search")
                        .param("q", "nonexistentterm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void imageSearchIsScopedToTheOwner() throws Exception {
        when(ocrEngine.enabled()).thenReturn(true);
        when(ocrEngine.recognize(any())).thenReturn("Private architecture diagram");
        String ownerToken = registerAndGetToken("img-owner");
        String strangerToken = registerAndGetToken("img-stranger");

        MvcResult upload = mockMvc.perform(multipart("/api/documents")
                        .file(pngUpload("private-arch.png"))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();
        long imageId = objectMapper
                .readTree(mockMvc.perform(get("/api/images/search")
                                .param("q", "architecture")
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(get("/api/images/search")
                        .param("q", "architecture")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/images/" + imageId + "/content")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }
}
