package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import com.example.aidocumentassistant.storage.Storage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ImageAssetService {
    private final ImageAssetRepository imageAssets;
    private final Storage storage;

    public ImageAssetService(ImageAssetRepository imageAssets, Storage storage) {
        this.imageAssets = imageAssets;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ImageAssetDtos.ImageSearchResponse> search(
            AuthenticatedUser principal,
            String query,
            int limit,
            Long documentId
    ) {
        String sanitized = query == null ? "" : query.trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        int capped = Math.max(1, Math.min(limit, 50));
        List<ImageAsset> assets = documentId == null
                ? imageAssets.searchForOwner(principal.id(), sanitized)
                : imageAssets.searchForOwnerInDocument(principal.id(), documentId, sanitized);
        return assets.stream()
                .limit(capped)
                .map(asset -> new ImageAssetDtos.ImageSearchResponse(
                        asset.getId(),
                        asset.getDocument().getId(),
                        asset.getDocument().getFilename(),
                        asset.getPage(),
                        asset.getWidth(),
                        asset.getHeight(),
                        snippet(asset.getOcrText())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImageAssetDtos.ImageContent content(AuthenticatedUser principal, Long id) {
        ImageAsset asset = imageAssets.findByIdForOwner(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        String key = asset.getStoragePath();
        if (!storage.exists(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image file no longer exists");
        }
        return new ImageAssetDtos.ImageContent(asset.getContentType(), key, storage.size(key));
    }

    private String snippet(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return "";
        }
        String flattened = ocrText.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 160 ? flattened : flattened.substring(0, 160) + "…";
    }
}
