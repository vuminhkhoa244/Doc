package com.example.aidocumentassistant.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.example.aidocumentassistant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloudinary-backed {@link Storage}, used when {@code app.storage.type=cloudinary}.
 *
 * <p>Keys are Cloudinary {@code public_id}s (without resource type or extension). Uploads go to
 * the folder configured by {@code app.cloudinary.folder}, keeping the user's own cloud tidy.
 * Documents are stored as {@code raw} resources (arbitrary bytes, served as-is); extracted image
 * assets are stored as {@code image} resources so Cloudinary can transcode/serve them natively.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cloudinary")
public class CloudinaryStorage implements Storage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorage.class);
    private static final String FOLDER_DOCUMENTS = "documents";
    private static final String FOLDER_ASSETS = "assets";

    private final Cloudinary cloudinary;
    private final String folder;

    public CloudinaryStorage(AppProperties properties) {
        AppProperties.Cloudinary config = properties.cloudinary();
        if (!config.enabled()) {
            throw new IllegalStateException(
                    "app.storage.type=cloudinary but app.cloudinary.* is not fully configured");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("cloud_name", config.cloudName());
        params.put("api_key", config.apiKey());
        params.put("api_secret", config.apiSecret());
        this.cloudinary = new Cloudinary(params);
        this.folder = config.folder() == null || config.folder().isBlank()
                ? "ai-doc-assistant"
                : config.folder();
    }

    @Override
    public String store(String key, byte[] bytes, String contentType) throws IOException {
        String publicId = publicIdFor(key);
        Map<String, Object> options = new HashMap<>(ObjectUtils.emptyMap());
        options.put("public_id", publicId);
        options.put("resource_type", resourceTypeFor(key));
        options.put("overwrite", true);
        try {
            cloudinary.uploader().upload(bytes, options);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Cloudinary upload failed for public_id={} resource_type={}", publicId,
                    options.get("resource_type"), exception);
            throw new IOException("Cloudinary upload failed for " + key, exception);
        }
        return key;
    }

    @Override
    public InputStream openRead(String key) throws IOException {
        return new ByteArrayInputStream(readAll(key));
    }

    @Override
    public Path materialize(String key) throws IOException {
        Path temp = Files.createTempFile("cloudinary-", "-" + safeSuffix(key));
        try {
            copyTo(key, temp);
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
        return temp;
    }

    @Override
    public void copyTo(String key, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = openRead(key)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public byte[] readAll(String key) throws IOException {
        // Cloudinary's Java SDK does not expose a direct download-to-bytes helper on Uploader.
        // Fetch the file via the public delivery URL and buffer it.
        String url = cloudinary.url()
                .resourceType(resourceTypeFor(key))
                .generate(publicIdFor(key));
        return fetch(url);
    }

    @Override
    public void writeTo(String key, OutputStream out) throws IOException {
        out.write(readAll(key));
    }

    @Override
    public boolean exists(String key) {
        try {
            ApiResponse response = cloudinary.api()
                    .resource(publicIdFor(key), resourceOptionsFor(key));
            return response.get("public_id") != null;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public long size(String key) {
        try {
            ApiResponse response = cloudinary.api()
                    .resource(publicIdFor(key), resourceOptionsFor(key));
            Object bytes = response.get("bytes");
            return bytes instanceof Number number ? number.longValue() : -1;
        } catch (Exception exception) {
            return -1;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            cloudinary.uploader().destroy(publicIdFor(key), resourceOptionsFor(key));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Cloudinary destroy failed for " + key, exception);
        }
    }

    private String publicIdFor(String key) {
        return folder + "/" + key;
    }

    private String folderFor(String key) {
        if (key.startsWith(FOLDER_ASSETS + "/")) {
            return folder + "/" + FOLDER_ASSETS;
        }
        return folder + "/" + FOLDER_DOCUMENTS;
    }

    private String resourceTypeFor(String key) {
        // Keys under assets/ are PNG/JPEG image assets; everything else is a raw document.
        return key.startsWith(FOLDER_ASSETS + "/") ? "image" : "raw";
    }

    private Map<String, Object> resourceOptionsFor(String key) {
        Map<String, Object> options = new HashMap<>(ObjectUtils.emptyMap());
        options.put("resource_type", resourceTypeFor(key));
        return options;
    }

    private String safeSuffix(String key) {
        int slash = key.lastIndexOf('/');
        String name = slash >= 0 ? key.substring(slash + 1) : key;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "bin";
    }

    private byte[] fetch(String url) throws IOException {
        try (InputStream in = new java.net.URL(url).openStream()) {
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new IOException("Failed to download from Cloudinary: " + url, exception);
        }
    }
}
