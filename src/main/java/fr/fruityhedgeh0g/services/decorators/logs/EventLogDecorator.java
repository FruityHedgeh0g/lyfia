package fr.fruityhedgeh0g.services.decorators.logs;

import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.dtos.eventDtos.MonGroupeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RegistrationDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterExportDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.PhoneRequiredException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.services.interfaces.EventService;
import io.quarkus.logging.Log;
import io.vavr.control.Try;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@Priority(200)
@Decorator
public class EventLogDecorator implements EventService{

    @Inject
    @Delegate
    EventService eventService;

    @Override
    public List<EventDto> listAll(boolean seesPlanification, UUID viewerId) {
        Log.debugf("Retrieving all events...");
        return Try.of(() -> eventService.listAll(seesPlanification, viewerId))
                .onSuccess(events -> Log.debugf("%d events retrieved.",events.size()))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving events."))
                .get();
    }

    @Override
    public EventDto getById(UUID eventId, boolean seesPlanification, UUID viewerId) {
        Log.debugf("Retrieving event by id %s...",eventId);
        return Try.of(() -> eventService.getById(eventId, seesPlanification, viewerId))
                .onSuccess(event -> Log.debugf("Event retrieved: "+event.toString()))
                .onFailure(t -> {
                    switch (t) {
                        case UnknownResourceException ex -> Log.warnf("Event with id %s not found.", eventId);
                        default -> Log.errorf(t, "An error occurred while retrieving event.");
                    }
                })
                .get();
    }

    @Override
    public EventDto create(EventDto eventDto) {
        Log.debugf("Creating event %s...", eventDto.getName());
        return Try.of(() -> eventService.create(eventDto))
                .onSuccess(event -> Log.infof("Event %s created.", event.getEventId()))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Event refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Event refused, unknown Secteur: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred while creating event.");
                    }
                })
                .get();
    }

    @Override
    public EventDto update(EventDto eventDto) {
        Log.debugf("Updating event %s...", eventDto.getEventId());
        return Try.of(() -> eventService.update(eventDto))
                .onSuccess(event -> Log.debugf("Event updated."))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Event update refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Event %s not found.", eventDto.getEventId());
                        default -> Log.errorf(t, "An error occurred while updating event.");
                    }
                })
                .get();
    }

    @Override
    public EventDto changeStatus(UUID eventId, EventStatusEnum status) {
        Log.debugf("Moving event %s to %s...", eventId, status.id());
        return Try.of(() -> eventService.changeStatus(eventId, status))
                .onSuccess(event -> Log.infof("Event %s is now %s.", eventId, status.id()))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Status change refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Event %s not found.", eventId);
                        default -> Log.errorf(t, "An error occurred while changing the event status.");
                    }
                })
                .get();
    }

    @Override
    public RegistrationDto signUp(UUID eventId, UUID personId, UUID groupId) {
        Log.debugf("%s signs up for event %s...", personId, eventId);
        return Try.of(() -> eventService.signUp(eventId, personId, groupId))
                .onSuccess(r -> Log.infof("%s signed up for event %s: %s.", personId, eventId, r.status().id()))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Sign-up refused: %s", ex.getMessage());
                        case PhoneRequiredException ex -> Log.debugf("Sign-up waiting for a phone number: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Sign-up refused: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred during a sign-up.");
                    }
                })
                .get();
    }

    @Override
    public void withdraw(UUID eventId, UUID personId) {
        Log.debugf("%s withdraws from event %s...", personId, eventId);
        Try.run(() -> eventService.withdraw(eventId, personId))
                .onSuccess(v -> Log.infof("%s withdrew from event %s.", personId, eventId))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Withdrawal refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Withdrawal refused: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred during a withdrawal.");
                    }
                })
                .get();
    }

    @Override
    public List<RegistrationDto> registrationsOf(UUID personId) {
        Log.debugf("Retrieving the sign-ups of %s...", personId);
        return Try.of(() -> eventService.registrationsOf(personId))
                .onFailure(t -> Log.errorf(t, "An error occurred while retrieving sign-ups."))
                .get();
    }

    @Override
    public RosterDto roster(UUID eventId) {
        Log.debugf("Retrieving the roster of event %s...", eventId);
        return Try.of(() -> eventService.roster(eventId))
                .onFailure(t -> {
                    switch (t) {
                        case UnknownResourceException ex -> Log.warnf("Event %s not found.", eventId);
                        default -> Log.errorf(t, "An error occurred while retrieving a roster.");
                    }
                })
                .get();
    }

    @Override
    public RosterDto promote(UUID eventId, UUID personId) {
        Log.debugf("Moving %s up on event %s...", personId, eventId);
        return Try.of(() -> eventService.promote(eventId, personId))
                .onSuccess(r -> Log.infof("%s moved up to Participant of event %s.", personId, eventId))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Move up refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Move up refused: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred while moving someone up.");
                    }
                })
                .get();
    }

    @Override
    public RosterDto removeFromRoster(UUID eventId, UUID personId) {
        Log.debugf("Removing %s from event %s...", personId, eventId);
        return Try.of(() -> eventService.removeFromRoster(eventId, personId))
                .onSuccess(r -> Log.infof("%s removed from event %s.", personId, eventId))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Removal refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Removal refused: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred while removing someone from an event.");
                    }
                })
                .get();
    }

    @Override
    public RegistrationDto requestGroup(UUID eventId, UUID personId, UUID groupId) {
        return logged(() -> eventService.requestGroup(eventId, personId, groupId),
                "Demande de groupe of " + personId + " for group " + groupId + " at event " + eventId);
    }

    @Override
    public RegistrationDto decideDemande(UUID eventId, UUID personId, Actor actor, boolean accept) {
        return logged(() -> eventService.decideDemande(eventId, personId, actor, accept),
                (accept ? "Acceptance" : "Refusal") + " of the Demande of " + personId + " at event " + eventId + " by " + actor.personId());
    }

    @Override
    public RosterDto placeInGroup(UUID eventId, UUID personId, UUID groupId) {
        return logged(() -> eventService.placeInGroup(eventId, personId, groupId),
                "Placement of " + personId + " in group " + groupId + " at event " + eventId);
    }

    @Override
    public RegistrationDto signUpAsPassager(UUID eventId, UUID personId, UUID pilotePersonId) {
        Log.debugf("%s signs up for event %s as passager of %s...", personId, eventId, pilotePersonId);
        return Try.of(() -> eventService.signUpAsPassager(eventId, personId, pilotePersonId))
                .onSuccess(r -> Log.infof("%s signed up for event %s as passager: %s.", personId, eventId, r.status().id()))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("Sign-up refused: %s", ex.getMessage());
                        case PhoneRequiredException ex -> Log.debugf("Sign-up waiting for a phone number: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("Sign-up refused: %s", ex.getMessage());
                        default -> Log.errorf(t, "An error occurred during a sign-up.");
                    }
                })
                .get();
    }

    @Override
    public List<NestedUserDto> pilotesOf(UUID eventId) {
        return logged(() -> eventService.pilotesOf(eventId), "Pilotes of event " + eventId);
    }

    @Override
    public RosterExportDto exportRoster(UUID eventId) {
        return logged(() -> eventService.exportRoster(eventId), "Spreadsheet export of event " + eventId);
    }

    @Override
    public void doCloseEventsOfSector(UUID sectorId) {
        logged(() -> {
            eventService.doCloseEventsOfSector(sectorId);
            return null;
        }, "[INTERNAL] Closing the events of sector " + sectorId);
    }

    @Override
    public RosterDto setGroupMaximum(UUID eventId, UUID groupId, Integer maximum) {
        return logged(() -> eventService.setGroupMaximum(eventId, groupId, maximum),
                "Maximum " + maximum + " for group " + groupId + " at event " + eventId);
    }

    @Override
    public RegistrationDto takeOutOfGroup(UUID eventId, UUID personId, Actor actor) {
        return logged(() -> eventService.takeOutOfGroup(eventId, personId, actor),
                "Removal of " + personId + " from their group at event " + eventId + " by " + actor.personId());
    }

    @Override
    public MonGroupeDto monGroupe(UUID chefId) {
        return logged(() -> eventService.monGroupe(chefId), "Mon groupe of " + chefId);
    }

    /** Logs a Groupe action at event level: refusals as warnings, anything else as errors. */
    private <T> T logged(io.vavr.CheckedFunction0<T> action, String what) {
        Log.debugf("%s...", what);
        return Try.of(action)
                .onSuccess(r -> Log.debugf("%s: done.", what))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("%s refused: %s", what, ex.getMessage());
                        case ForbiddenActionException ex -> Log.warnf("%s forbidden: %s", what, ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("%s refused: %s", what, ex.getMessage());
                        default -> Log.errorf(t, "%s failed.", what);
                    }
                })
                .get();
    }
}
