package fr.fruityhedgeh0g.storage;

import java.io.InputStream;

/** A file read from the {@link MediaStorage}: its type, its size and its content, as a stream. */
public record StoredFile(String mimeType, long size, InputStream content) {
}
