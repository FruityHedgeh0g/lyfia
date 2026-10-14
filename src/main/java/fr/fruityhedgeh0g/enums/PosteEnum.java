package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;

import java.util.Arrays;

/** What a person signed up for an Event does at it, chosen per Event; every Poste counts towards the maximums. */
public enum PosteEnum {
    CONDUCTEUR,
    /** Rides behind a conducteur of their Groupe. */
    PASSAGER,
    /** Drives the van accompanying the motards; takes no passager. */
    CHAUFFEUR_CAMIONNETTE,
    /** Stationed at one place along the route; with no Groupe. */
    POINT_FIXE,
    /** Logistics, buvette or security, with a Groupe de service. */
    SERVICE;

    @JsonValue
    public String id() {
        return name().toLowerCase();
    }

    /** The Poste named by its id, as the API reads it. */
    public static PosteEnum of(String id) {
        return Arrays.stream(values()).filter(poste -> poste.id().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidResourceException("Unknown Poste: " + id));
    }

    /** The kind of Groupe someone at this Poste is with; null for a Point fixe, who is with no Groupe. */
    public GroupKindEnum groupKind() {
        return switch (this) {
            case CONDUCTEUR, PASSAGER, CHAUFFEUR_CAMIONNETTE -> GroupKindEnum.MOTARDS;
            case SERVICE -> GroupKindEnum.SERVICE;
            case POINT_FIXE -> null;
        };
    }

    public boolean fits(GroupKindEnum kind) {
        return groupKind() == kind;
    }
}
