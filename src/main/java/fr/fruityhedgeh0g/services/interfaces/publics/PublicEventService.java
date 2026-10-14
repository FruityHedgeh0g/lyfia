package fr.fruityhedgeh0g.services.interfaces.publics;

import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.dtos.eventDtos.MonGroupeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RegistrationDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterExportDto;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Events are never deleted: they are cancelled instead. People sign up per Journée, at a Poste and with a Groupe that
 * stay the same for the whole Event (ADR 0011); a list of Journées left empty means every Journée concerned.
 */
public interface PublicEventService {
    /** @param seesPlanification true for the Bureau and above; others never see Planification Events */
    /** {@code viewerId}: also the Events in Planification that person rides at, as a Groupe's Chef (ADR 0010). */
    List<EventDto> listAll(boolean seesPlanification, UUID viewerId);
    EventDto getById(@NotNull UUID eventId, boolean seesPlanification, UUID viewerId);
    /** A new Event belongs to a Secteur and starts in Planification. */
    EventDto create(@NotNull @Valid EventDto eventDto);
    /** Shortening an Event removes the sign-ups of the Journées it no longer has; Chefs are at the new ones. */
    EventDto update(@NotNull @Valid EventDto eventDto);
    /** Manual transition, checked against the Event's current status. */
    EventDto changeStatus(@NotNull UUID eventId, @NotNull EventStatusEnum status);

    /**
     * What one signs up with: a Poste, the Journées (none: every Journée), and either a Demande de groupe or, for a
     * passager, the name of a conducteur to come with ("je viens avec"), typed freely: who is signed up is never listed.
     */
    record SignUp(@NotNull PosteEnum poste, UUID groupId, String conducteurName, List<LocalDate> journees) {
    }

    /**
     * Signs the person up while the Event is Ouvert or Complet, each Journée a confirmed place while Ouvert and under
     * that Journée's maximum, otherwise on its Liste d'attente. A passager naming a conducteur rides behind the one
     * whose name matches (NameMatching), among those with no passager yet one of the chosen Journées, on the Journées
     * both are at, waiting as they do, and takes their Groupe or Demande; without a clear match the sign-up is refused
     * (ConducteurNotFoundException). Signing up again at the same Poste adds the Journées not signed up for yet.
     */
    RegistrationDto signUp(@NotNull UUID eventId, @NotNull UUID personId, @NotNull SignUp signUp);
    /** A new Demande de groupe, for someone signed up and not yet with a Groupe. */
    RegistrationDto requestGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull UUID groupId);
    /**
     * Accepts or refuses a pending Demande: the asked Groupe's Chef (by Affectation) or the Bureau.
     * Accepting is refused when the Groupe is at its maximum any Journée of the person; the Demande then stays pending.
     */
    RegistrationDto decideDemande(@NotNull UUID eventId, @NotNull UUID personId, @NotNull Actor actor, boolean accept);
    /**
     * The Bureau places someone confirmed at least one Journée in a Groupe, within its maximum each Journée;
     * {@code withPassager}: a conducteur's passagers follow, otherwise their pairings end.
     */
    RosterDto placeInGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull UUID groupId, boolean withPassager);
    /**
     * The Bureau changes someone's Poste, not for a conducteur who has a passager nor for the Chef leading their
     * Groupe; they leave a Groupe whose kind no longer fits.
     */
    RosterDto changePoste(@NotNull UUID eventId, @NotNull UUID personId, @NotNull PosteEnum poste);
    /** The Bureau sets a Groupe's maximum at the Event (a Groupe of the Event's Secteur); null or 0 removes it. */
    RosterDto setGroupMaximum(@NotNull UUID eventId, @NotNull UUID groupId, Integer maximum);
    /**
     * The Groupe's Chef or the Bureau takes the person out of the Groupe; they stay signed up.
     * {@code withPassager}: a conducteur's passagers leave it too, otherwise their pairings end.
     */
    RegistrationDto takeOutOfGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull Actor actor, boolean withPassager);
    /**
     * The Bureau or the Chef of the passager's Groupe pairs them with a conducteur of that Groupe for a Journée: one
     * without a passager that Journée, waiting or confirmed as the passager is.
     */
    RegistrationDto pair(@NotNull UUID eventId, @NotNull UUID passagerId, @NotNull LocalDate journee, @NotNull UUID conducteurId, @NotNull Actor actor);
    /** The Bureau or the Chef of the passager's Groupe ends their pairing that Journée; they stay signed up. */
    RegistrationDto unpair(@NotNull UUID eventId, @NotNull UUID passagerId, @NotNull LocalDate journee, @NotNull Actor actor);
    /** Mon groupe for a Chef de groupe: empty without an Affectation. */
    MonGroupeDto monGroupe(@NotNull UUID chefId);

    /** Who acts, for checks that go beyond the Role (a Chef acts only on the Groupe they lead). */
    record Actor(UUID personId, boolean bureau) {
    }
    /**
     * Withdraws from some Journées (none: all of them) until Archivé; nobody moves up. {@code withPassager}: a
     * conducteur's passager goes too those Journées, otherwise they stay without a conducteur.
     */
    void withdraw(@NotNull UUID eventId, @NotNull UUID personId, @NotNull List<LocalDate> journees, boolean withPassager);
    List<RegistrationDto> registrationsOf(@NotNull UUID personId);

    /** The Bureau's view of an Event's Participants and Liste d'attente for a Journée (null: its first). */
    RosterDto roster(@NotNull UUID eventId, LocalDate journee);
    /**
     * The roster as an .xlsx file: one sheet per Journée, with each person's Poste, Groupe and pairing,
     * Participants first, then the Liste d'attente.
     */
    RosterExportDto exportRoster(@NotNull UUID eventId);
    /**
     * Moves someone up from the Liste d'attente of some Journées (none: every one they wait at), only while under
     * that Journée's maximum; a pair moves up together, taking two places.
     */
    RosterDto promote(@NotNull UUID eventId, @NotNull UUID personId, @NotNull List<LocalDate> journees);
    /**
     * Removes someone from some Journées (none: all of them); the freed places are not given to anyone.
     * {@code withPassager}: a conducteur's passager goes too, otherwise they stay without a conducteur.
     */
    RosterDto removeFromRoster(@NotNull UUID eventId, @NotNull UUID personId, @NotNull List<LocalDate> journees, boolean withPassager);
}
