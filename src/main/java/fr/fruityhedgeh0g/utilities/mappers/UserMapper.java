package fr.fruityhedgeh0g.utilities.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.entities.UserEntity;
import io.vertx.core.json.Json;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", uses = {EventMapper.class, GroupMapper.class, SectorMapper.class})
public interface UserMapper {
    static final ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

    @Mapping(target = "bureauFunction", ignore = true)
    @Mapping(target = "sector", ignore = true)
    UserEntity toEntity(UserDto dto);

    UserDto toDto(UserEntity entity);

    NestedUserDto toNestedDto(UserEntity entity);

    @Mapping(target = "role",ignore = true)
    @Mapping(target = "bureauFunction",ignore = true)
    @Mapping(target = "sector",ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserEntity partialDtoToEntity(@MappingTarget UserEntity userEntity, UserDto userDto);

//    @Mappings({
//            @Mapping(target = "organizedEvents", qualifiedByName = "EventDtoToNestedEntity"),
//            @Mapping(target = "participatedEvents", qualifiedByName = "EventDtoToNestedEntity"),
//            @Mapping(target = "createdEvents", qualifiedByName = "EventDtoToNestedEntity")
//    })


//    @Mappings({
//            @Mapping(target = "organizedEvents", qualifiedByName = "EventEntityToNestedDto"),
//            @Mapping(target = "participatedEvents", qualifiedByName = "EventEntityToNestedDto"),
//            @Mapping(target = "createdEvents", qualifiedByName = "EventEntityToNestedDto")
//    })




//    @Named("UserEntityToNestedDto")
//    @Mappings({
//        @Mapping(target = "organizedEvents", ignore = true),
//        @Mapping(target = "participatedEvents", ignore = true),
//        @Mapping(target = "createdEvents", ignore = true)
//    })
//    UserDto toNestedDto(UserEntity entity);
//
//    @Named("UserDtoToNestedEntity")
//    @Mappings({
//            @Mapping(target = "organizedEvents", ignore = true),
//            @Mapping(target = "participatedEvents", ignore = true),
//            @Mapping(target = "createdEvents", ignore = true)
//    })
//    UserEntity toNestedEntity(UserDto dto);
//
//    @Named("UserDtoSetToNestedEntitySet")
//    default Set<UserEntity> toNestedEntitySet(Set<UserDto> dtos){
//        return dtos.stream().map(this::toNestedEntity).collect(Collectors.toSet());
//    };
//
//    @Named("UserEntitySetToNestedDtoSet")
//    default Set<UserDto> toNestedDtoSet(Set<UserEntity> entities){
//        return entities.stream().map(this::toNestedDto).collect(Collectors.toSet());
//    };
//
}
