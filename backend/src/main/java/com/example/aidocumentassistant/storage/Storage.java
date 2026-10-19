package com.example.aidocumentassistant.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Abstraction over where uploaded documents and extracted image assets live.
 *
 * <p>Implementations must be safe to call with a {@code key} that was produced by
 * {@link #store}. For Cloudinary the key is a {@code public_id}; for local storage it is a
 * path relative to the configured upload directory. Callers must never assume the key is a
 * filesystem path — use {@link #openRead} / {@link #exists} to interact with it.
 */
public interface Storage {

    /**
     * Stores {@code bytes} under {@code key}, creating or overwriting as needed.
     *
     * @param key        storage key (relative path for local, public_id for Cloudinary)
     * @param bytes      payload to persist
     * @param contentType MIME type recorded with the stored object where supported
     * @return the key under which the object was stored (may differ from {@code key} if the
     *         backend namespaces or sanitizes it)
     * @throws IOException if the write fails
     */
    String store(String key, byte[] bytes, String contentType) throws IOException;

    /**
     * Opens a stream over the stored object's content.
     *
     * @param key storage key
     * @return a fresh input stream; caller must close it
     * @throws IOException if the object is missing or cannot be read
     */
    InputStream openRead(String key) throws IOException;

    /**
     * Materializes the stored object as a local file, for callers (PDFBox, Tesseract,
     * ImageIO) that require a real {@link Path}. Implementations may copy to a temp file.
     *
     * @param key storage key
     * @return a local file holding the object's content; caller should treat it as read-only
     *         and may delete it when done
     * @throws IOException if the object is missing or cannot be materialized
     */
    Path materialize(String key) throws IOException;

    /**
     * Copies the stored object into {@code target}.
     *
     * @param key    storage key
     * @param target destination file
     * @throws IOException if the object is missing or cannot be copied
     */
    void copyTo(String key, Path target) throws IOException;

    /**
     * Reads the stored object fully into memory.
     *
     * @param key storage key
     * @return the object's bytes
     * @throws IOException if the object is missing or cannot be read
     */
    byte[] readAll(String key) throws IOException;

    /**
     * Writes the stored object into {@code out} without materializing it locally first.
     *
     * @param key storage key
     * @param out destination stream; caller closes it
     * @throws IOException if the object is missing or cannot be streamed
     */
    void writeTo(String key, OutputStream out) throws IOException;

    /**
     * Reports whether an object exists under {@code key}.
     *
     * @param key storage key
     * @return true if present
     */
    boolean exists(String key);

    /**
     * Reports the stored object's size in bytes, or {@code -1} if unknown.
     *
     * @param key storage key
     * @return size in bytes, or -1
     */
    long size(String key);

    /**
     * Deletes the stored object. Deleting a missing key is a no-op.
     *
     * @param key storage key
     * @throws IOException if the delete fails
     */
    void delete(String key) throws IOException;
}
