package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.exceptions.FeatureOffException;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.security.Viewer;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Where the back end refuses what a Feature that is off covers, for everyone, the Super admin included (ADR 0009).
 * A Feature the database does not know is a lever never pulled: on.
 */
@ApplicationScoped
public class FeatureLevers {

    @Inject
    FeatureRepository featureRepository;

    @Inject
    Viewer viewer;

    public boolean isOn(FeatureEnum feature) {
        return QuarkusTransaction.joiningExisting().call(() -> featureRepository.findByName(feature.id())
                .map(FeatureEntity::getIsActive)
                .orElse(true));
    }

    /** Refuses what {@code feature} covers while it is off. */
    public void require(FeatureEnum feature) {
        if (!isOn(feature)) throw refused(feature);
    }

    /**
     * Refuses what the public sees of a content Feature while it is off; the Bureau, who prepares that
     * content in Administration, still reads it.
     */
    public void requireForPublic(FeatureEnum feature) {
        if (!viewer.preparesContent()) require(feature);
    }

    /** Counted for the Journal in a transaction of its own: the refused request's rolls back. */
    private FeatureOffException refused(FeatureEnum feature) {
        Log.warnf("Refused while the Feature %s is off: %s", feature.id(), viewer.describe());
        QuarkusTransaction.requiringNew().run(() ->
                featureRepository.update("refusedCount = refusedCount + 1 where name = ?1", feature.id()));
        return new FeatureOffException(feature);
    }
}
