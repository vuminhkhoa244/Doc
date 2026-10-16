package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/images")
public class ImageAssetController {
    private final ImageAssetService imageAssetService;

    public ImageAssetController(ImageAssetService imageAssetService) {
        this.imageAssetService = imageAssetService;
    }

    @GetMapping("/search")
    public List<ImageAssetDtos.ImageSearchResponse> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "limit", defaultValue = "12") int limit,
            @RequestParam(value = "documentId", required = false) Long documentId
    ) {
        return imageAssetService.search(principal, query, limit, documentId);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) throws IOException {
        ImageAssetDtos.ImageContent file = imageAssetService.content(principal, id);
        InputStream inputStream = Files.newInputStream(file.path());
        MediaType mediaType = file.contentType() != null
                ? MediaType.parseMediaType(file.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "private, max-age=300")
                .contentLength(Files.size(file.path()))
                .body(new InputStreamResource(inputStream));
    }
}
