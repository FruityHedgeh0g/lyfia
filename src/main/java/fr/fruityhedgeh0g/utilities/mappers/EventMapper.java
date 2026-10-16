package fr.fruityhedgeh0g.utilities.mappers;

import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.entities.EventEntity;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", uses = {UserMapper.class, SectorMapper.class})
public interface EventMapper {

    /** Status and Secteur are set by the service, never copied from a request body. */
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sector", ignore = true)
    @Mapping(target = "groupMaximums", ignore = true)
    @Mapping(target = "media", ignore = true)
    EventEntity toEntity(EventDto dto);

    @Mapping(target = "status", expression = "java(entity.currentStatus())")
    @Mapping(target = "sectorId", source = "sector.sectorId")
    @Mapping(target = "imageMediaId", source = "media.mediaId")
    EventDto toDto(EventEntity entity);

    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sector", ignore = true)
    @Mapping(target = "groupMaximums", ignore = true)
    @Mapping(target = "media", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    EventEntity partialDtoToEntity(@MappingTarget EventEntity eventEntity, EventDto eventDto);
}
