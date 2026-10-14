package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.PosteEnum;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventRegistrationRepository implements PanacheRepositoryBase<EventRegistrationEntity, UUID> {

    public Optional<EventRegistrationEntity> findByEventAndPerson(UUID eventId, UUID personId) {
        return find("event.eventId = ?1 and person.userId = ?2", eventId, personId).firstResultOptional();
    }

    /** Every sign-up for the Event, oldest first. */
    public List<EventRegistrationEntity> listByEvent(UUID eventId) {
        return list("event.eventId = ?1 order by signedUpAt, registrationId", eventId);
    }

    /** Sign-ups riding with, or asking for, the Groupe at any Event, oldest first. */
    public List<EventRegistrationEntity> listByGroup(UUID groupId) {
        return list("group.groupId = ?1 or (demandeGroup.groupId = ?1 and demandeStatus = ?2) order by signedUpAt, registrationId",
                groupId, fr.fruityhedgeh0g.enums.DemandeStatusEnum.EN_ATTENTE);
    }

    public List<EventRegistrationEntity> listByPerson(UUID personId) {
        return list("person.userId = ?1 order by event.startDateTime", personId);
    }

    public EventRegistrationEntity persistConfirmed(EventEntity event, UserEntity person) {
        return persistConfirmed(event, person, PosteEnum.CONDUCTEUR);
    }

    public EventRegistrationEntity persistWaiting(EventEntity event, UserEntity person) {
        return persistWaiting(event, person, PosteEnum.CONDUCTEUR);
    }

    public EventRegistrationEntity persistConfirmed(EventEntity event, UserEntity person, PosteEnum poste) {
        return persistSignUp(event, person, poste, false);
    }

    public EventRegistrationEntity persistWaiting(EventEntity event, UserEntity person, PosteEnum poste) {
        return persistSignUp(event, person, poste, true);
    }

    private EventRegistrationEntity persistSignUp(EventEntity event, UserEntity person, PosteEnum poste, boolean waiting) {
        // Every Journée of the Event
        EventRegistrationEntity registration = EventRegistrationEntity.signUp(event, person, poste, waiting);
        persist(registration);
        return registration;
    }
}
