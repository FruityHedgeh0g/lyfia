package fr.fruityhedgeh0g.dtos.featureDtos;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    /** It also has a lever per Secteur (Actualités, Inscription aux événements, Export de la liste). */
    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    Boolean perSecteur;

    /** The Secteurs where it is turned off, whatever its lever for the whole site. */
    @JsonView({Views.Basic.class, Views.UpdateResponse.class})
    List<UUID> offSectors;

    /** Attempts refused since it was last turned off, for the whole site or a Secteur. */
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
