package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.enums.RideModeEnum;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One's own sign-up for an Event, as shown in "Mes événements", with its Groupe and Demande de groupe;
 * {@code pilote} names a passager's pilote (null for a pilote); {@code leadsGroup} while one rides as the Chef of
 * the Groupe, a place nobody but a change of Affectation takes away (ADR 0010).
 */
public record RegistrationDto(UUID eventId, RideModeEnum mode, RegistrationStatusEnum status, LocalDateTime signedUpAt,
                              GroupRefDto group, DemandeDto demande, NestedUserDto pilote, boolean leadsGroup) {
}
