package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.configurations.FeatureSectorLeverEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FeatureSectorLeverRepository implements PanacheRepositoryBase<FeatureSectorLeverEntity, UUID> {

    public Optional<FeatureSectorLeverEntity> find(String feature, UUID sectorId) {
        return find("feature = ?1 and sector.sectorId = ?2", feature, sectorId).firstResultOptional();
    }

    /** The Secteurs where the Feature is turned off. */
    public List<UUID> offSecteurs(String feature) {
        return find("feature = ?1 and isActive = false", feature).stream()
                .map(lever -> lever.getSector().getSectorId())
                .toList();
    }
}
