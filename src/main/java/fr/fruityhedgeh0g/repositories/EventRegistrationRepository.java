package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
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

    public long countParticipants(UUID eventId) {
        return count("event.eventId = ?1 and waiting = false", eventId);
    }

    /** Everyone riding with the Groupe at the Event. */
    public long countInGroup(UUID eventId, UUID groupId) {
        return count("event.eventId = ?1 and group.groupId = ?2", eventId, groupId);
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
        return persistSignUp(event, person, false);
    }

    public EventRegistrationEntity persistWaiting(EventEntity event, UserEntity person) {
        return persistSignUp(event, person, true);
    }

    private EventRegistrationEntity persistSignUp(EventEntity event, UserEntity person, boolean waiting) {
        EventRegistrationEntity registration = EventRegistrationEntity.conducteur(event, person, waiting);
        persist(registration);
        return registration;
    }
}
