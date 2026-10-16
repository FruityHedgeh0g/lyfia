package fr.fruityhedgeh0g.dtos.galleryDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.GalleryPhotoEntity;
import fr.fruityhedgeh0g.enums.VisibiliteEnum;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A Photo de la Galerie as the Galerie shows it: its Media (whose files are at /api/medias/{mediaId}/content), what
 * it tells about itself (ADR 0013), and, for the Bureau of its Secteur only, its {@code visibilite} and where the
 * site uses it ({@code usedBy}: it then stays Publique and is not deleted); {@code proposition} while a Chef de
 * groupe's Proposition waits for the Bureau.
 */
public record GalleryPhotoDto(UUID photoId, UUID mediaId, String alt, UUID sectorId, int annee, EventRefDto event,
                              List<GroupRefDto> groups, VisibiliteEnum visibilite, LocalDateTime depositedAt,
                              List<String> usedBy, boolean proposition) {

    /** The Event a photo shows. */
    public record EventRefDto(UUID eventId, String name, LocalDateTime startDateTime) {
        public static EventRefDto of(EventEntity event) {
            return event == null ? null : new EventRefDto(event.getEventId(), event.getName(), event.getStartDateTime());
        }
    }

    public static GalleryPhotoDto of(GalleryPhotoEntity photo, boolean managed, List<String> usedBy) {
        return new GalleryPhotoDto(photo.getPhotoId(), photo.getMedia().getMediaId(), photo.getMedia().getAlt(),
                photo.getSector().getSectorId(), photo.getAnnee(), EventRefDto.of(photo.getEvent()),
                photo.getGroups().stream().map(GroupRefDto::of).sorted(Comparator.comparing(GroupRefDto::name)).toList(),
                managed ? photo.getVisibilite() : null, photo.getDepositedAt(), usedBy, photo.isProposition());
    }
}
