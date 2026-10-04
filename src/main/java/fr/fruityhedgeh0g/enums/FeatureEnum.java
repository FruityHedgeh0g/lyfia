package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * The Features: the emergency levers the Super admin pulls to stop one capability of the site (ADR 0009).
 * The id is the Feature's name in the {@code features} table and in the API.
 */
public enum FeatureEnum {
    /** The online donation workflow, once it exists; never the Faire un don page. */
    DONS_EN_LIGNE("dons-en-ligne", false),
    INSCRIPTION_EVENEMENTS("inscription-evenements", true),
    GALERIE_PHOTOS("galerie-photos", false),
    /** Site-wide only until Posts belong to a Secteur (#26). */
    ACTUALITES("actualites", false),
    CARROUSEL("carrousel", false),
    DEPOT_MEDIAS("depot-medias", false),
    DEMANDES_FONCTIONNALITE("demandes-fonctionnalite", false),
    EXPORT_LISTE("export-liste", true),
    /** Also opens or closes Keycloak's registration form, where people register. */
    INSCRIPTION_SITE("inscription-site", false);

    private final String id;
    private final boolean perSecteur;

    FeatureEnum(String id, boolean perSecteur) {
        this.id = id;
        this.perSecteur = perSecteur;
    }

    /** The Feature, by its id; empty for a name the site does not know. */
    public static Optional<FeatureEnum> of(String id) {
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }

    /** It is about what belongs to a Secteur, so it also has a lever per Secteur. */
    public boolean perSecteur() {
        return perSecteur;
    }

    @JsonValue
    public String id() {
        return id;
    }
}
