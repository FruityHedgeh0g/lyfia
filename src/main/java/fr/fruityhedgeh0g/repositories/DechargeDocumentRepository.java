package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DechargeDocumentRepository implements PanacheRepositoryBase<DechargeDocumentEntity, UUID> {

    /** The version in force: the latest uploaded. */
    public Optional<DechargeDocumentEntity> findCurrent() {
        return find("order by uploadedAt desc").firstResultOptional();
    }
}
