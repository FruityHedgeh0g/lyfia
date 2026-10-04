package fr.fruityhedgeh0g.utilities.mappers;

import fr.fruityhedgeh0g.dtos.postDtos.NestedPostDto;
import fr.fruityhedgeh0g.dtos.postDtos.PostDto;
import fr.fruityhedgeh0g.entities.PostEntity;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", uses = {MediaMapper.class, UserMapper.class})
public interface PostMapper {
    /** Status and author are set by the service, never copied from a request body. */
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "banner", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "sector", ignore = true)
    PostEntity toEntity(PostDto dto);

    @Mapping(target = "sectorId", source = "sector.sectorId")
    PostDto toDto(PostEntity entity);

    NestedPostDto toNestedDto(PostEntity entity);

    @Mapping(target = "postId", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "banner", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "sector", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PostEntity partialDtoToEntity(@MappingTarget PostEntity postEntity, PostDto postDto);
}
