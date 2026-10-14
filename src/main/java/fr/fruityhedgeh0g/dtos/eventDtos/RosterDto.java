package fr.fruityhedgeh0g.dtos.eventDtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Event's roster for one Journée (ADR 0011): its Participants and Liste d'attente, each in sign-up order, and each
 * Groupe of its Secteur that Journée; {@code journees} counts every Journée of the Event.
 */
public record RosterDto(UUID eventId, LocalDate journee, List<JourneeCountDto> journees, Integer maxParticipants,
                        List<RosterEntryDto> participants, List<RosterEntryDto> waiting, List<GroupRosterDto> groups) {

    /** How many people are signed up for a Journée: confirmed and on its Liste d'attente. */
    public record JourneeCountDto(LocalDate journee, int participants, int waiting) {
    }
}
