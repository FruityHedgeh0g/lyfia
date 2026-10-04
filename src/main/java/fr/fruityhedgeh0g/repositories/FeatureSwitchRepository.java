package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.configurations.FeatureSwitchEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FeatureSwitchRepository implements PanacheRepositoryBase<FeatureSwitchEntity, UUID> {

    /** One page of the Journal, newest first. */
    public List<FeatureSwitchEntity> page(int index, int size) {
        return findAll(Sort.descending("switchedAt").and("switchId")).page(Page.of(index, size)).list();
    }

    /** The last switch of a Feature's lever for the whole site. */
    public Optional<FeatureSwitchEntity> lastSiteWide(String feature) {
        return find("feature = ?1 and sector is null", Sort.descending("switchedAt"), feature).firstResultOptional();
    }
}
