package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;

import java.util.Arrays;

/**
 * The files a Media keeps (ADR 0014): its original and, for a Photo de la Galerie, the variants the depositor's
 * browser made; a fixed list, so that they can all be made again one day.
 */
public enum MediaVariantEnum {
    ORIGINAL,
    /** About 2000 px on the long side, for the large view. */
    AFFICHAGE,
    /** About 400 px on the long side, for the grids. */
    VIGNETTE;

    @JsonValue
    public String id() {
        return name().toLowerCase();
    }

    /** The variant named by its id, the original when none is named. */
    public static MediaVariantEnum of(String id) {
        if (id == null || id.isBlank()) return ORIGINAL;
        return Arrays.stream(values()).filter(v -> v.id().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidResourceException("Unknown variant: " + id));
    }

    /** The key of this variant among the files of the Media whose file key is {@code fileKey}. */
    public String keyOf(String fileKey) {
        return fileKey + "/" + id();
    }
}
