package fr.fruityhedgeh0g.services.interfaces.publics;

import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.dtos.eventDtos.MonGroupeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RegistrationDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterExportDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Events are never deleted: they are cancelled instead. */
public interface PublicEventService {
    /** @param seesPlanification true for the Bureau and above; others never see Planification Events */
    /** {@code viewerId}: also the Events in Planification that person rides at, as a Groupe's Chef (ADR 0010). */
    List<EventDto> listAll(boolean seesPlanification, UUID viewerId);
    EventDto getById(@NotNull UUID eventId, boolean seesPlanification, UUID viewerId);
    /** A new Event belongs to a Secteur and starts in Planification. */
    EventDto create(@NotNull @Valid EventDto eventDto);
    EventDto update(@NotNull @Valid EventDto eventDto);
    /** Manual transition, checked against the Event's current status. */
    EventDto changeStatus(@NotNull UUID eventId, @NotNull EventStatusEnum status);

    /**
     * Signs the person up at a Poste other than passager while the Event is Ouvert or Complet: a Participant
     * while Ouvert and under the maximum, otherwise on the Liste d'attente. Signing up
     * again returns the existing sign-up.
     */
    RegistrationDto signUp(@NotNull UUID eventId, @NotNull UUID personId, @NotNull PosteEnum poste, UUID groupId);
    /**
     * Signs the person up as passager of a conducteur already signed up for the Event, with the conducteur's
     * placement and Groupe; refused when that would exceed the Event's or the Groupe's maximum.
     */
    RegistrationDto signUpAsPassager(@NotNull UUID eventId, @NotNull UUID personId, @NotNull UUID conducteurPersonId);
    /** The conducteurs signed up for the Event, whom a passager can ride with. */
    List<NestedUserDto> conducteursOf(@NotNull UUID eventId);
    /** A new Demande de groupe, for someone signed up and not yet riding with a Groupe. */
    RegistrationDto requestGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull UUID groupId);
    /**
     * Accepts or refuses a pending Demande: the asked Groupe's Chef (by Affectation) or the Bureau.
     * Accepting is refused at the Groupe's maximum; the Demande then stays pending.
     */
    RegistrationDto decideDemande(@NotNull UUID eventId, @NotNull UUID personId, @NotNull Actor actor, boolean accept);
    /** The Bureau places a Participant in a Groupe directly, within the Groupe's maximum. */
    RosterDto placeInGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull UUID groupId);
    /**
     * The Bureau changes someone's Poste, never to passager nor for the Chef leading their Groupe; they leave a Groupe
     * whose kind no longer fits.
     */
    RosterDto changePoste(@NotNull UUID eventId, @NotNull UUID personId, @NotNull PosteEnum poste);
    /** The Bureau sets a Groupe's maximum at the Event (a Groupe of the Event's Secteur); null or 0 removes it. */
    RosterDto setGroupMaximum(@NotNull UUID eventId, @NotNull UUID groupId, Integer maximum);
    /** The Groupe's Chef or the Bureau takes the person out of the Groupe; they stay signed up. */
    RegistrationDto takeOutOfGroup(@NotNull UUID eventId, @NotNull UUID personId, @NotNull Actor actor);
    /** Mon groupe for a Chef de groupe: empty without an Affectation. */
    MonGroupeDto monGroupe(@NotNull UUID chefId);

    /** Who acts, for checks that go beyond the Role (a Chef acts only on the Groupe they lead). */
    record Actor(UUID personId, boolean bureau) {
    }
    /** Withdraws a Participant or someone on the Liste d'attente, with a conducteur's passagers, until Archivé; nobody moves up. */
    void withdraw(@NotNull UUID eventId, @NotNull UUID personId);
    List<RegistrationDto> registrationsOf(@NotNull UUID personId);

    /** The Bureau's view of an Event's Participants and Liste d'attente. */
    RosterDto roster(@NotNull UUID eventId);
    /**
     * The roster as an .xlsx file: one tab per Groupe of the Secteur, one for Participants without
     * a Groupe, one for the Liste d'attente (whose people are in no other tab).
     */
    RosterExportDto exportRoster(@NotNull UUID eventId);
    /** Moves someone up from the Liste d'attente, only while under the overall maximum. */
    RosterDto promote(@NotNull UUID eventId, @NotNull UUID personId);
    /** Removes someone from the Event, with a conducteur's passagers; the freed places are not given to anyone. */
    RosterDto removeFromRoster(@NotNull UUID eventId, @NotNull UUID personId);
}
