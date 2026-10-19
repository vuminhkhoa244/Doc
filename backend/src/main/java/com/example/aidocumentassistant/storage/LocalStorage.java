package com.example.aidocumentassistant.storage;

import com.example.aidocumentassistant.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Filesystem-backed {@link Storage}, rooted at {@code app.storage.upload-dir}.
 *
 * <p>Keys are paths relative to the upload directory (e.g. {@code assets/foo.png}). This is the
 * default implementation and the one used by tests and local development.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorage implements Storage {

    private final Path root;

    public LocalStorage(AppProperties properties) {
        this.root = properties.storage().uploadDir();
    }

    @Override
    public String store(String key, byte[] bytes, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return key;
    }

    @Override
    public InputStream openRead(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public Path materialize(String key) throws IOException {
        return resolve(key);
    }

    @Override
    public void copyTo(String key, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(resolve(key), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public byte[] readAll(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    @Override
    public void writeTo(String key, OutputStream out) throws IOException {
        Files.copy(resolve(key), out);
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public long size(String key) {
        try {
            return Files.size(resolve(key));
        } catch (IOException exception) {
            return -1;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Storage key escapes upload directory: " + key);
        }
        return resolved;
    }
}
