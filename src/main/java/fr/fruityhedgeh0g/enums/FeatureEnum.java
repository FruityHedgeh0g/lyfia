package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The Features: the emergency levers the Super admin pulls to stop one capability of the site (ADR 0009).
 * The id is the Feature's name in the {@code features} table and in the API.
 */
public enum FeatureEnum {
    /** The online donation workflow, once it exists; never the Faire un don page. */
    DONS_EN_LIGNE("dons-en-ligne"),
    INSCRIPTION_EVENEMENTS("inscription-evenements"),
    GALERIE_PHOTOS("galerie-photos");

    private final String id;

    FeatureEnum(String id) {
        this.id = id;
    }

    @JsonValue
    public String id() {
        return id;
    }
}
