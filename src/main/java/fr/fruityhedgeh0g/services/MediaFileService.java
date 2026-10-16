package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.dtos.mediaDtos.MediaDto;
import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.entities.medias.PhotoEntity;
import fr.fruityhedgeh0g.enums.MediaVariantEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.GalleryPhotoRepository;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import fr.fruityhedgeh0g.storage.MediaStorage;
import fr.fruityhedgeh0g.storage.StoredFile;
import fr.fruityhedgeh0g.utilities.mappers.MediaMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The files behind the medias: uploaded by the Bureau, kept through the {@link MediaStorage} (ADR 0014) and served
 * to everyone. Images only: a video does not belong in the database, and an SVG could carry a script.
 */
@ApplicationScoped
public class MediaFileService {

    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** The largest image used as a Post's banner, in the carousel or as the logo (ADR 0008). */
    public static final long MAX_SIZE = 8L * 1024 * 1024;

    /** A file as it is uploaded. */
    public record Upload(String filename, String mimeType, long size, InputStream content) {}

    @Inject MediaRepository mediaRepository;
    @Inject MediaStorage storage;
    @Inject MediaMapper mediaMapper;
    @Inject MediaUses mediaUses;
    @Inject GalleryPhotoRepository photoRepository;

    /** An image used outside the Galerie (a banner, the carousel, the logo): its original only. */
    @Transactional
    public MediaDto upload(Upload file, String alt) {
        requireImage(file, MAX_SIZE);
        return mediaMapper.toDto(store(file, Map.of(), alt));
    }

    /**
     * A Media with its original and the variants its depositor's browser made (ADR 0014); the server decodes none
     * of them, it checks their types and weights only.
     */
    @Transactional
    public MediaEntity store(Upload original, Map<MediaVariantEnum, Upload> variants, String alt) {
        String fileKey = UUID.randomUUID().toString();
        storage.put(MediaVariantEnum.ORIGINAL.keyOf(fileKey), original.mimeType(), original.content(), original.size());
        variants.forEach((variant, file) -> storage.put(variant.keyOf(fileKey), file.mimeType(), file.content(), file.size()));

        PhotoEntity photo = new PhotoEntity();
        photo.setOriginalFilename(original.filename() == null || original.filename().isBlank() ? "image" : original.filename());
        photo.setFileKey(fileKey);
        // The discriminator column, required by validation though Hibernate writes it from the class
        photo.setContentType("PHOTO");
        photo.setMimeType(original.mimeType());
        photo.setFileSize(original.size());
        photo.setAlt(alt == null || alt.isBlank() ? null : alt.trim());
        mediaRepository.persist(photo);
        return photo;
    }

    /** Refuses anything but a JPEG, PNG, WebP or GIF image of at most {@code maxBytes}. */
    public static void requireImage(Upload file, long maxBytes) {
        if (file == null) throw new InvalidResourceException("An upload carries a file.");
        if (file.mimeType() == null || !IMAGE_TYPES.contains(file.mimeType()))
            throw new InvalidResourceException("Only JPEG, PNG, WebP and GIF images are accepted: " + file.mimeType());
        if (file.size() == 0 || file.size() > maxBytes)
            throw new InvalidResourceException("An image weighs at most " + maxBytes / 1024 / 1024 + " MB: " + file.size() + " bytes");
    }

    @Transactional
    public MediaDto describe(UUID mediaId, String alt) {
        MediaEntity media = mediaRepository.findByIdOptional(mediaId)
                .orElseThrow(() -> new UnknownResourceException("Media not found: " + mediaId));
        media.setAlt(alt == null || alt.isBlank() ? null : alt.trim());
        return mediaMapper.toDto(media);
    }

    @Transactional
    public Optional<MediaEntity> find(UUID mediaId) {
        return mediaRepository.findByIdOptional(mediaId);
    }

    /** A file of the Media: the variant asked for, its original when it has none (a Media outside the Galerie). */
    @Transactional
    public Optional<StoredFile> file(MediaEntity media, MediaVariantEnum variant) {
        return storage.open(variant.keyOf(media.getFileKey()))
                .or(() -> variant == MediaVariantEnum.ORIGINAL ? Optional.empty() : storage.open(MediaVariantEnum.ORIGINAL.keyOf(media.getFileKey())));
    }

    /**
     * The Bureau deletes an image outside the Galerie once nothing uses it any more; a Photo de la Galerie is deleted
     * through the Galerie.
     */
    @Transactional
    public void deleteUnused(UUID mediaId) {
        MediaEntity media = mediaRepository.findByIdOptional(mediaId)
                .orElseThrow(() -> new UnknownResourceException("Media not found: " + mediaId));
        if (photoRepository.isInGalerie(mediaId))
            throw new InvalidResourceException("A Photo de la Galerie is deleted through the Galerie: " + mediaId);
        mediaUses.requireUnused(mediaId, "An image in use is not deleted");
        delete(media);
    }

    /** The Media and every one of its files. */
    @Transactional
    public void delete(MediaEntity media) {
        Arrays.stream(MediaVariantEnum.values()).forEach(variant -> storage.delete(variant.keyOf(media.getFileKey())));
        mediaRepository.delete(media);
    }
}
