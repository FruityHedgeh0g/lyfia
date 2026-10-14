package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** A Groupe de motards covers part of the Secteur on the road; a Groupe de service runs one task at the Event. */
public enum GroupKindEnum {
    MOTARDS,
    SERVICE;

    @JsonValue
    public String id() {
        return name().toLowerCase();
    }

    /** The Poste its Chef de groupe holds at the Events it is at (ADR 0010). */
    public PosteEnum chefPoste() {
        return this == SERVICE ? PosteEnum.SERVICE : PosteEnum.CONDUCTEUR;
    }
}
