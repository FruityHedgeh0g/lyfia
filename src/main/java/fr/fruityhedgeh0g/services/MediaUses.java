package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.enums.VisibiliteEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.repositories.GalleryPhotoRepository;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where the site uses an image: a Post's banner or photos, a slide of the carousel, an Event's image, the logo. Only
 * a Publique Photo de la Galerie can be used there, since all of these are public; while it is, it can be neither
 * made less visible nor deleted.
 */
@ApplicationScoped
public class MediaUses {

    /** The setting naming the logo's Media (the Super admin's Configuration). */
    static final String NAVBAR_LOGO = "navbar.logo";

    @Inject MediaRepository mediaRepository;
    @Inject GalleryPhotoRepository photoRepository;
    @Inject EntityManager entityManager;

    /** The Media to use as an image, none for none; refused when it is a Photo de la Galerie not Publique. */
    public MediaEntity usable(UUID mediaId) {
        if (mediaId == null) return null;
        MediaEntity media = mediaRepository.findByIdOptional(mediaId)
                .orElseThrow(() -> new InvalidResourceException("Unknown media: " + mediaId));
        photoRepository.findByMedia(mediaId)
                .filter(photo -> photo.getVisibilite() != VisibiliteEnum.PUBLIQUE)
                .ifPresent(photo -> {
                    throw new InvalidResourceException("Only a Publique photo of the Galerie is used on the public site: " + mediaId);
                });
        return media;
    }

    /** What uses the Media, as the Bureau reads it: « Actualité « … » », « Carrousel « … » »… */
    public List<String> usedBy(UUID mediaId) {
        List<String> uses = new ArrayList<>();
        titles("select p.title from PostEntity p where p.banner.mediaId = ?1", mediaId).forEach(t -> uses.add("Actualité « " + t + " »"));
        titles("select p.title from PostEntity p join p.attachments a where a.mediaId = ?1", mediaId).forEach(t -> uses.add("Photos de l'actualité « " + t + " »"));
        titles("select c.title from CarouselItemEntity c where c.media.mediaId = ?1", mediaId).forEach(t -> uses.add("Carrousel « " + t + " »"));
        titles("select e.name from EventEntity e where e.media.mediaId = ?1", mediaId).forEach(t -> uses.add("Événement « " + t + " »"));
        if (!entityManager.createQuery("select c.name from ConfigurationEntity c where c.name = ?1 and c.value = ?2", String.class)
                .setParameter(1, NAVBAR_LOGO).setParameter(2, mediaId.toString()).getResultList().isEmpty())
            uses.add("Logo du site");
        return uses;
    }

    public boolean isUsed(UUID mediaId) {
        return !usedBy(mediaId).isEmpty();
    }

    /** Refuses what would take a used Media away from where it is used. */
    public void requireUnused(UUID mediaId, String what) {
        List<String> uses = usedBy(mediaId);
        if (!uses.isEmpty())
            throw new InvalidResourceException(what + ": the image is used by " + String.join(", ", uses));
    }

    private List<String> titles(String query, UUID mediaId) {
        return entityManager.createQuery(query, String.class).setParameter(1, mediaId).getResultList().stream().distinct().toList();
    }
}
