package fr.fruityhedgeh0g.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a Participant rides at an Event, chosen per Event. */
public enum RideModeEnum {
    CONDUCTEUR,
    /** Rides with a named conducteur of the same Event and always follows them. */
    PASSAGER;

    @JsonValue
    public String id() {
        return name().toLowerCase();
    }
}
