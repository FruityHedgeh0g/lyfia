package fr.fruityhedgeh0g.dtos.featureDtos;

import fr.fruityhedgeh0g.entities.configurations.FeatureSwitchEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** A Journal entry; {@code sectorId} null means the whole site, {@code switchedBy} is a name. */
public record FeatureSwitchEntryDto(UUID id, String feature, UUID sectorId, String sectorName, boolean isActive,
                                    String reason, String switchedBy, LocalDateTime switchedAt) {

    public static FeatureSwitchEntryDto of(FeatureSwitchEntity entry) {
        return new FeatureSwitchEntryDto(entry.getSwitchId(), entry.getFeature(),
                entry.getSector() == null ? null : entry.getSector().getSectorId(),
                entry.getSector() == null ? null : entry.getSector().getName(),
                entry.getIsActive(), entry.getReason(), nameOf(entry), entry.getSwitchedAt());
    }

    static String nameOf(FeatureSwitchEntity entry) {
        return entry.getSwitchedBy() == null ? null
                : entry.getSwitchedBy().getFirstName() + " " + entry.getSwitchedBy().getLastName();
    }
}
