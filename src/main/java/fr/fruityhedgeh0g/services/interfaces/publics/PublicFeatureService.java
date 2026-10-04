package fr.fruityhedgeh0g.services.interfaces.publics;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureSwitchEntryDto;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public interface PublicFeatureService {
    List<FeatureDto> listAll();
    FeatureDto getByName(@NotNull String name);

    /** Pulls a Feature's lever for the whole site and writes it in the Journal; {@code by} may be unknown. */
    FeatureDto switchLever(@NotNull String name, boolean active, String reason, UUID by);

    /** Pulls a Feature's lever for one Secteur, independent of the site-wide one, and writes it in the Journal. */
    FeatureDto switchSectorLever(@NotNull String name, @NotNull UUID sectorId, boolean active, String reason, UUID by);

    /** One page of the Journal, newest first. */
    List<FeatureSwitchEntryDto> journal(int page, int size);
}
