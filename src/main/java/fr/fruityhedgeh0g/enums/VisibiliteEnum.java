package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;

import java.util.Arrays;

/** Who sees a Photo de la Galerie; it starts as a Brouillon and only the Bureau changes it. */
public enum VisibiliteEnum {
    /** The Bureau of its Secteur, and whoever deposited it. */
    BROUILLON,
    /** Anyone signed in. */
    INSCRITS,
    /** Everyone. */
    PUBLIQUE;

    @JsonValue
    public String id() {
        return name().toLowerCase();
    }

    /** The Visibilité named by its id, as the API reads it. */
    public static VisibiliteEnum of(String id) {
        return Arrays.stream(values()).filter(v -> v.id().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidResourceException("Unknown Visibilité: " + id));
    }
}
