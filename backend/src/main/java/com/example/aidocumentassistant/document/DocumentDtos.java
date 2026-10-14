package com.example.aidocumentassistant.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Locale;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record RenameRequest(
            @NotBlank @Size(max = 255) String filename
    ) {
    }

    public record PageChunkResponse(
            int chunkIndex,
            int page,
            String text
    ) {
    }

    public record DocumentResponse(
            Long id,
            String filename,
            long size,
            String contentType,
            Instant uploadTime,
            String status,
            int chunks,
            String errorMessage
    ) {
        public static DocumentResponse from(Document document) {
            return new DocumentResponse(
                    document.getId(),
                    document.getFilename(),
                    document.getSize(),
                    document.getContentType(),
                    document.getUploadTime(),
                    document.getStatus().name().toLowerCase(Locale.ROOT),
                    document.getChunks(),
                    document.getErrorMessage()
            );
        }
    }
}
