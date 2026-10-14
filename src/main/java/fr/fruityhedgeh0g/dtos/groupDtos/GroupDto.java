package fr.fruityhedgeh0g.dtos.groupDtos;

import fr.fruityhedgeh0g.enums.GroupKindEnum;
import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.sectorDtos.NestedSectorDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.dtos.Views;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class GroupDto {

    @JsonView({Views.Minimal.class,Views.CreationResponse.class,Views.Update.class})
    UUID groupId;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String name;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String description;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String area;

    /** motards or service; motards when left out at creation. */
    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    GroupKindEnum kind;

    /** Set only through an Affectation, never from a create or update body. */
    @JsonView(Views.Basic.class)
    NestedUserDto chef;

    @JsonView(Views.Detailed.class)
    NestedSectorDto sector;

    /** The Groupe's Secteur: named at creation by the Super admin; below, always the creator's own. */
    @JsonView({Views.Basic.class,Views.Creation.class})
    UUID sectorId;

}
