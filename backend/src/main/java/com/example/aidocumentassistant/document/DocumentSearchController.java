package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class DocumentSearchController {
    private final VectorSearchService vectorSearchService;

    public DocumentSearchController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping
    public DocumentSearchDtos.SearchResponse search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DocumentSearchDtos.SearchRequest request
    ) {
        return vectorSearchService.search(principal, request);
    }
}
