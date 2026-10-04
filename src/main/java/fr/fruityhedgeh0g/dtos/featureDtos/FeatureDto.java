package fr.fruityhedgeh0g.dtos.featureDtos;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** A Feature, with its lever's last switch for the whole site (from the Journal). */
@Value
@Builder(toBuilder = true)
public class FeatureDto {
    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    String name;

    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    String description;

    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    Boolean isActive;

    /** Attempts refused since it was last turned off. */
    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    Long refusedCount;

    /** Who last switched it, by name; null if never switched. */
    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    String lastSwitchedBy;

    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    LocalDateTime lastSwitchedAt;

    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    String lastReason;
}
