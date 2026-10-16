package fr.fruityhedgeh0g.dtos.galleryDtos;

import java.util.List;
import java.util.UUID;

/**
 * What the Bureau tells about a Photo de la Galerie, all of it at once: its description, its Event (none: its
 * {@code annee} is given instead) and its Groupes.
 */
public record GalleryPhotoChangeDto(String alt, UUID eventId, Integer annee, List<UUID> groupIds) {
}
