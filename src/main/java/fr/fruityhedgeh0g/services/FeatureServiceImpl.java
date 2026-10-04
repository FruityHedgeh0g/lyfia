package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.entities.configurations.ConfigurationEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.keycloak.KeycloakRegistration;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.services.interfaces.FeatureService;
import fr.fruityhedgeh0g.utilities.mappers.FeatureMapper;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;

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
    KeycloakRegistration keycloakRegistration;

    @Override
    public List<FeatureDto> listAll() {
        return featureRepository.listAll()
                .stream()
                .map(featureMapper::toDto)
                .toList();
    }

    @Override
    public FeatureDto getByName(String name) {
        return featureMapper.toDto(
                featureRepository.findByName(name)
                .orElseThrow(() -> new UnknownResourceException("Feature not found: " + name))
        );
    }

    @Override
    @Transactional
    public FeatureDto update(FeatureDto featureDto) {
        FeatureEntity featureEntity = featureRepository.findByName(featureDto.getName())
                .orElseThrow(() -> new UnknownResourceException("Feature not found: " + featureDto.getName()));
        // Keycloak's form first: if it cannot be closed, the lever must not look pulled (ADR 0009)
        if (FeatureEnum.INSCRIPTION_SITE.id().equals(featureEntity.getName()) && featureDto.getIsActive() != null)
            keycloakRegistration.allowSelfRegistration(featureDto.getIsActive());

        featureEntity = featureMapper.partialDtoToEntity(featureEntity,featureDto);
        featureRepository.persist(featureEntity);

        return featureMapper.toDto(featureEntity);
    }

//    @Override
//    @Transactional
//    public Try<FeatureDto> getFeatureByName( String name) {
//        Log.infof("Getting feature by name: %s", name);
//        return Try.of(() -> featureRepository.findByIdOptional(name).orElseThrow(
//                () -> new UnknownResourceException("Feature not found: " + name)))
//        .map(featureMapper::toDto)
//        .onFailure(e -> {
//            if (e instanceof UnknownResourceException ex) {
//                Log.warn(ex.getMessage());
//            } else {
//                Log.errorf(e,"Error getting feature by name: %s", name );
//            }
//        });
//    }
//
//    @Override
//    @Transactional
//    public Try<List<FeatureDto>> getAllFeatures() {
//        Log.info("Getting all features");
//        return Try.of(() -> featureRepository
//                .findAll()
//                .stream()
//                .map(featureMapper::toDto)
//                .toList())
//                .onFailure(e -> {
//                    Log.errorf(e,"Error getting all features");
//                });
//    }
//
//    public Try<FeatureDto> updateFeature( FeatureDto dto) {
//        Log.infof("Updating feature: %s", dto.getName());
//        return Try.of(() -> {
//            Log.infof("Updating feature: %s", dto.getName());
//            FeatureEntity feature = featureRepository.findByIdOptional(dto.getName())
//                    .orElseThrow(() -> new UnknownResourceException("Feature not found: " + dto.getName()));
//
//            featureMapper.partialDtoToEntity(feature, dto);
//            return featureMapper.toDto(feature);
//        });
//    }
}
