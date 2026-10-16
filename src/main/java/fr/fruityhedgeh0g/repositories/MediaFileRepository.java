package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.medias.MediaFileEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MediaFileRepository implements PanacheRepositoryBase<MediaFileEntity, String> {
}
