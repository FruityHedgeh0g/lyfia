package fr.fruityhedgeh0g.dtos.eventDtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A conducteur signed up for an Event, for a passager to name: the Journées they still have no passager. */
public record ConducteurDto(UUID userId, String firstName, String lastName, List<LocalDate> freeJournees) {
}
