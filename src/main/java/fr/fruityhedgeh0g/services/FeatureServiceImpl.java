package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureSwitchEntryDto;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureSwitchEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.keycloak.KeycloakRegistration;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.repositories.FeatureSwitchRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.services.interfaces.FeatureService;
import fr.fruityhedgeh0g.utilities.mappers.FeatureMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class FeatureServiceImpl implements FeatureService {
    @Inject
    FeatureMapper featureMapper;

    @Inject
    FeatureRepository featureRepository;

    @Inject
    FeatureSwitchRepository switchRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    KeycloakRegistration keycloakRegistration;

    @Override
    public List<FeatureDto> listAll() {
        return featureRepository.listAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public FeatureDto getByName(String name) {
        return toDto(featureOrThrow(name));
    }

    @Override
    @Transactional
    public FeatureDto switchLever(String name, boolean active, String reason, UUID by) {
        FeatureEntity feature = featureOrThrow(name);
        // Keycloak's form first: if it cannot be closed, the lever must not look pulled (ADR 0009)
        if (FeatureEnum.INSCRIPTION_SITE.id().equals(feature.getName()))
            keycloakRegistration.allowSelfRegistration(active);

        boolean wasActive = feature.getIsActive();
        // Refusals count from the moment it is turned off
        if (wasActive && !active) feature.setRefusedCount(0);
        feature.setIsActive(active);
        String why = reason == null || reason.isBlank() ? null : reason.trim();
        switchRepository.persist(FeatureSwitchEntity.builder()
                .feature(feature.getName())
                .isActive(active)
                .reason(why)
                .switchedBy(by == null ? null : userRepository.findById(by))
                .switchedAt(LocalDateTime.now())
                .build());
        Log.infof("Feature %s turned %s (was %s) for the whole site by %s%s", feature.getName(),
                active ? "on" : "off", wasActive ? "on" : "off", by, why == null ? "" : ": " + why);
        return toDto(feature);
    }

    @Override
    public List<FeatureSwitchEntryDto> journal(int page, int size) {
        return switchRepository.page(page, size).stream().map(FeatureSwitchEntryDto::of).toList();
    }

    private FeatureEntity featureOrThrow(String name) {
        return featureRepository.findByName(name)
                .orElseThrow(() -> new UnknownResourceException("Feature not found: " + name));
    }

    /** The Feature with its lever's last switch for the whole site. */
    private FeatureDto toDto(FeatureEntity feature) {
        FeatureDto dto = featureMapper.toDto(feature);
        return switchRepository.lastSiteWide(feature.getName())
                .map(last -> dto.toBuilder()
                        .lastSwitchedBy(FeatureSwitchEntryDto.of(last).switchedBy())
                        .lastSwitchedAt(last.getSwitchedAt())
                        .lastReason(last.getReason())
                        .build())
                .orElse(dto);
    }
}
