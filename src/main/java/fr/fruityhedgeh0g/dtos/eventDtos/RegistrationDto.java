package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.PosteEnum;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One's own sign-up for an Event, as shown in "Mes événements": the Poste, Groupe and Demande de groupe of the whole
 * Event, and each Journée signed up for ({@code journees}). {@code status} is en_attente only while waiting every
 * Journée; {@code conducteur} names the conducteur of a passager's first paired Journée; {@code leadsGroup} while one
 * rides as the Chef of the Groupe, a place nobody but a change of Affectation takes away (ADR 0010).
 */
public record RegistrationDto(UUID eventId, PosteEnum poste, RegistrationStatusEnum status, LocalDateTime signedUpAt,
                              GroupRefDto group, DemandeDto demande, NestedUserDto conducteur, boolean leadsGroup,
                              List<JourneeDto> journees) {

    /** A Journée signed up for: confirmed or waiting, and who rides with the person that Journée. */
    public record JourneeDto(LocalDate journee, RegistrationStatusEnum status, NestedUserDto conducteur, NestedUserDto passager) {
    }
}
