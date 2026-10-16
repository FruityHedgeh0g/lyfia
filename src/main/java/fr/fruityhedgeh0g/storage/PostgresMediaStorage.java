package fr.fruityhedgeh0g.storage;

import fr.fruityhedgeh0g.entities.medias.MediaFileEntity;
import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.repositories.MediaFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/** The files kept in the database, in {@code media_files} (ADR 0008, ADR 0014): the only storage for now. */
@ApplicationScoped
public class PostgresMediaStorage implements MediaStorage {

    @Inject
    MediaFileRepository fileRepository;

    @Override
    @Transactional
    public void put(String key, String mimeType, InputStream content, long size) {
        if (size > MediaFileEntity.MAX_SIZE)
            throw new InvalidResourceException("A file weighs at most " + MediaFileEntity.MAX_SIZE / 1024 / 1024 + " MB: " + key);
        if (fileRepository.findByIdOptional(key).isPresent())
            throw new DuplicateResourceException("A file is already kept under " + key);
        try {
            fileRepository.persist(new MediaFileEntity(key, mimeType, content.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @Transactional
    public Optional<StoredFile> open(String key) {
        return fileRepository.findByIdOptional(key)
                .map(file -> new StoredFile(file.getMimeType(), file.getContent().length, new ByteArrayInputStream(file.getContent())));
    }

    @Override
    @Transactional
    public void delete(String key) {
        fileRepository.deleteById(key);
    }

    @Override
    @Transactional
    public boolean exists(String key) {
        return fileRepository.count("fileKey", key) > 0;
    }
}
