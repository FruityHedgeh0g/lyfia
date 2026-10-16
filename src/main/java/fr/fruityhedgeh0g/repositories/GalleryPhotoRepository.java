package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.GalleryPhotoEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GalleryPhotoRepository implements PanacheRepositoryBase<GalleryPhotoEntity, UUID> {

    /** An Album: a Secteur's photos for one Année, in the order of ADR 0013 (latest Event first, then deposit order). */
    public List<GalleryPhotoEntity> listAlbum(UUID sectorId, int annee) {
        return list("from GalleryPhotoEntity p left join fetch p.event e where p.sector.sectorId = ?1 and p.annee = ?2 "
                + "order by e.startDateTime desc nulls last, p.depositedAt asc, p.photoId asc", sectorId, annee);
    }

    /** Every photo, for the list of Albums; their files are never loaded. */
    public List<GalleryPhotoEntity> listAllPhotos() {
        return listAll();
    }

    public Optional<GalleryPhotoEntity> findByMedia(UUID mediaId) {
        return find("media.mediaId", mediaId).firstResultOptional();
    }

    public boolean isInGalerie(UUID mediaId) {
        return count("media.mediaId", mediaId) > 0;
    }
}
