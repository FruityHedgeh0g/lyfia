package fr.fruityhedgeh0g.dtos.eventDtos;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.sectorDtos.NestedSectorDto;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class EventDto {

    @JsonView({Views.Minimal.class,Views.CreationResponse.class,Views.Update.class})
    UUID eventId;

    /** Current status, read only: changed through PUT /api/events/{id}/status. */
    @JsonView({Views.Minimal.class,Views.CreationResponse.class,Views.UpdateResponse.class})
    EventStatusEnum status;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String name;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String description;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    LocalDateTime startDateTime;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    LocalDateTime endDateTime;

    /** Its image's Media (files at /api/medias/{id}/content); none: the site's placeholder. */
    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    UUID imageMediaId;

    /** Secteur of the Event: required on creation, fixed afterwards. */
    @JsonView({Views.Basic.class,Views.Creation.class})
    UUID sectorId;

    @JsonView(Views.Basic.class)
    NestedSectorDto sector;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String latitude;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String longitude;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String address;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String city;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String country;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String postalCode;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String addressComplement;

    /** Optional overall maximum; 0 on an update removes it. */
    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    Integer maxParticipants;

}
