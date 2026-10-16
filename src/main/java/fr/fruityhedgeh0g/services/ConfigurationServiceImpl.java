package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.configurationDtos.ConfigurationDto;
import fr.fruityhedgeh0g.entities.configurations.ConfigurationEntity;
import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.ConfigurationRepository;
import fr.fruityhedgeh0g.services.interfaces.ConfigurationService;
import fr.fruityhedgeh0g.utilities.mappers.ConfigurationMapper;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.stream.Stream;
import java.util.Optional;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class ConfigurationServiceImpl implements ConfigurationService {

    @Inject
    ConfigurationRepository configurationRepository;

    @Inject
    ConfigurationMapper configurationMapper;

    @Override
    public List<ConfigurationDto> listAll() {
        List<ConfigurationDto> stored = configurationRepository.listAll()
                .stream()
                .map(configurationMapper::toDto)
                .toList();
        // The settings the site knows are listed with their default until the Super admin sets them
        List<ConfigurationDto> defaults = GallerySettings.DEFAULTS.entrySet().stream()
                .filter(d -> stored.stream().noneMatch(c -> c.getName().equals(d.getKey())))
                .map(d -> ConfigurationDto.builder().name(d.getKey()).value(d.getValue()).build())
                .toList();
        return Stream.concat(stored.stream(), defaults.stream()).toList();
    }

    @Override
    public ConfigurationDto getByName(String name) {
        return configurationMapper.toDto(
                configurationRepository.findByName(name)
                        .orElseThrow(() -> new UnknownResourceException("Configuration not found: " + name))
        );

    }

    @Override
    @Transactional
    public ConfigurationDto update(ConfigurationDto configurationDto) {
        GallerySettings.validate(configurationDto.getName(), configurationDto.getValue());
        ConfigurationEntity configurationEntity = configurationRepository.findByName(configurationDto.getName())
                // A known setting not set yet is created
                .or(() -> GallerySettings.DEFAULTS.containsKey(configurationDto.getName())
                        ? Optional.of(ConfigurationEntity.builder().name(configurationDto.getName()).value(configurationDto.getValue()).build())
                        : Optional.empty())
                .orElseThrow(() -> new UnknownResourceException("Configuration not found: " + configurationDto.getName()));

        configurationEntity = configurationMapper.partialDtoToEntity(configurationEntity,configurationDto);
        configurationRepository.persist(configurationEntity);

        return configurationMapper.toDto(configurationEntity);
    }

//    @Transactional
//    public Try<ConfigurationDto> getConfigurationByName( String name) {
//        Log.info("Getting configuration by name: " + name);
//        return Try.of(() -> configurationRepository
//                .findByIdOptional(name)
//                .orElseThrow(() -> new UnknownResourceException("Configuration not found: " + name)))
//                .map(configurationMapper::toDto)
//                .onFailure(e -> {
//                    if (e instanceof UnknownResourceException) {
//                        Log.warn("Configuration not found: " + name);
//                    }else {
//                        Log.error("Error getting configuration by name: " + name, e);
//                    }
//                });
//    }
//
//    @Transactional
//    public Try<List<ConfigurationDto>> getAllConfigurations() {
//        Log.info("Getting all configurations");
//        return Try.of(() ->configurationRepository.findAll()
//                .stream()
//                .map(configurationMapper::toDto)
//                .toList())
//                .onFailure(e -> Log.error("Error getting all configurations", e));
//    }
//
//    @Transactional
//    public Try<ConfigurationDto> updateConfiguration( ConfigurationDto dto) {
//        Log.info("Updating configuration: " + dto.getName());
//        return Try.of(() -> {
//            Log.debugf("Checking if configuration with name: %s already exists", dto.getName());
//            ConfigurationEntity configuration = configurationRepository.findByIdOptional(dto.getName())
//                    .orElseThrow(() -> new UnknownResourceException("Configuration not found: " + dto.getName()));
//
//            configurationMapper.partialDtoToEntity(configuration, dto);
//
//            return configurationMapper.toDto(configuration);
//        }).onFailure(e -> {
//            if (e instanceof UnknownResourceException) {
//                Log.warn(e.getMessage());
//            } else {
//                Log.error("Error updating configuration: " + dto.getName(), e);
//            }
//        });
//    }
}
