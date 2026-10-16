package fr.fruityhedgeh0g.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Where the files of the Medias are kept, by file key (ADR 0014): the database for now, an object storage later,
 * chosen by configuration without anything else changing. A file never changes: a new version is a new key.
 */
public interface MediaStorage {

    /** Keeps {@code content} under {@code key}, replacing nothing: a key is written once. */
    void put(String key, String mimeType, InputStream content, long size);

    Optional<StoredFile> open(String key);

    void delete(String key);

    boolean exists(String key);
}
