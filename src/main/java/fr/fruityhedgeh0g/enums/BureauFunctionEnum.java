package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a person sitting on their Secteur's Bureau is there (ADR 0001): held by everyone with the Bureau Role, and by
 * an Admin called to the Bureau. Grants no access: access stays the Role's.
 */
public enum BureauFunctionEnum {
    MEMBRE_DU_BUREAU,
    PRESIDENT;

    /** Name used in the API, e.g. {@code membre_du_bureau}. */
    @JsonValue
    public String id() {
        return name().toLowerCase();
    }
}
