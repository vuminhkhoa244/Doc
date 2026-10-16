package com.example.aidocumentassistant.document;

public final class ImageAssetDtos {
    private ImageAssetDtos() {
    }

    public record ImageSearchResponse(
            Long id,
            Long documentId,
            String filename,
            int page,
            int width,
            int height,
            String snippet
    ) {
    }

    public record ImageContent(String contentType, java.nio.file.Path path) {
    }
}
