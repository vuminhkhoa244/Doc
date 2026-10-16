package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentDtos.DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return documentService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDtos.DocumentResponse upload(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        return documentService.upload(principal, file);
    }

    @PatchMapping("/{id}")
    public DocumentDtos.DocumentResponse rename(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody DocumentDtos.RenameRequest request
    ) {
        return documentService.rename(principal, id, request.filename());
    }

    @PostMapping("/{id}/reindex")
    public DocumentDtos.DocumentResponse reindex(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        return documentService.reindex(principal, id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) throws IOException {
        DocumentService.DocumentFile file = documentService.download(principal, id);
        InputStream inputStream = Files.newInputStream(file.path());
        MediaType mediaType = file.contentType() != null
                ? MediaType.parseMediaType(file.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentLength(Files.size(file.path()))
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/{id}/chunks")
    public List<DocumentDtos.PageChunkResponse> pageChunks(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam("page") int page
    ) {
        return documentService.pageChunks(principal, id, page);
    }

    @GetMapping("/{id}/tables")
    public List<DocumentDtos.TableResponse> tables(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        return documentService.tables(principal, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        documentService.delete(principal, id);
    }
}
