package fr.fruityhedgeh0g.utilities.mappers;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "jakarta-cdi")
public interface FeatureMapper {
    /** The last switch comes from the Journal (FeatureServiceImpl). */
    @Mapping(target = "lastSwitchedBy", ignore = true)
    @Mapping(target = "lastSwitchedAt", ignore = true)
    @Mapping(target = "lastReason", ignore = true)
    @Mapping(target = "perSecteur", ignore = true)
    @Mapping(target = "offSectors", ignore = true)
    FeatureDto toDto(FeatureEntity entity);

    FeatureEntity toEntity(FeatureDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    FeatureEntity partialDtoToEntity(@MappingTarget FeatureEntity entity, FeatureDto dto);

}
