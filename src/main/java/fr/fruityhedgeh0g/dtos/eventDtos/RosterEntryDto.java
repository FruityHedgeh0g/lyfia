package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.RideModeEnum;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One person on an Event's roster, as the Bureau or their Chef de groupe sees it: {@code conducteur} for a passager,
 * {@code passagers} the number riding with a conducteur (they count towards every maximum with them), {@code leadsGroup}
 * for the Groupe's Chef, who stays with it until the Affectation changes (ADR 0010).
 */
public record RosterEntryDto(UUID personId, String firstName, String lastName, String phone, RideModeEnum mode,
                             LocalDateTime signedUpAt, GroupRefDto group, DemandeDto demande, NestedUserDto conducteur,
                             int passagers, boolean leadsGroup) {
}
