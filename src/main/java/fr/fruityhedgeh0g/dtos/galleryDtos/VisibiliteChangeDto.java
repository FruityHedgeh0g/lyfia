package fr.fruityhedgeh0g.dtos.galleryDtos;

import fr.fruityhedgeh0g.enums.VisibiliteEnum;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** The Bureau gives a selection of photos one Visibilité. */
public record VisibiliteChangeDto(@NotEmpty List<UUID> photoIds, @NotNull VisibiliteEnum visibilite) {
}
