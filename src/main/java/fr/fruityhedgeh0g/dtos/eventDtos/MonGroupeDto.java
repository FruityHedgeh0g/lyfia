package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.enums.EventStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mon groupe: the Groupe a Chef leads (null without an Affectation) and, per Event and Journée, its people and pending Demandes. */
public record MonGroupeDto(GroupRefDto group, List<MonGroupeDto.EventRoster> events) {

    /**
     * {@code maximum}: the Groupe's maximum at the Event (each Journée), null when unlimited; {@code members} and
     * {@code demandes}: everyone with or asking for the Groupe, any Journée; {@code journees}: the same, Journée by Journée.
     */
    public record EventRoster(UUID eventId, String name, LocalDateTime startDateTime, EventStatusEnum status, Integer maximum,
                              List<RosterEntryDto> members, List<RosterEntryDto> demandes, List<JourneeRoster> journees) {
    }

    /** The Groupe at one Journée: its people (with each pairing that Journée) and its pending Demandes, in sign-up order. */
    public record JourneeRoster(LocalDate journee, List<RosterEntryDto> members, List<RosterEntryDto> demandes) {
    }
}
