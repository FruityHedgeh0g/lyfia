package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.PosteEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One person on an Event's roster for one Journée, as the Bureau or their Chef de groupe sees it: {@code signedUpAt}
 * for that Journée (the order of its Liste d'attente), the {@code conducteur} a passager rides behind and the
 * {@code passager} riding behind a conducteur that Journée, {@code journees} every Journée the person signed up for,
 * {@code leadsGroup} for the Groupe's Chef, who stays with it until the Affectation changes (ADR 0010); for the Bureau
 * only, their {@code email}, {@code birthDate} and the {@code decharge} approved for a minor.
 */
public record RosterEntryDto(UUID personId, String firstName, String lastName, String phone, PosteEnum poste,
                             LocalDateTime signedUpAt, GroupRefDto group, DemandeDto demande, NestedUserDto conducteur,
                             NestedUserDto passager, boolean leadsGroup, List<LocalDate> journees,
                             String email, LocalDate birthDate, DechargeDto decharge) {
}
