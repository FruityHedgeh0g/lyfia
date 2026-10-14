package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.JourneeRegistrationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.UUID;

/** Sign-ups per Journée: the maximums and Listes d'attente count each Journée apart (ADR 0011). */
@ApplicationScoped
public class JourneeRegistrationRepository implements PanacheRepositoryBase<JourneeRegistrationEntity, UUID> {

    /** Confirmed places at the Event that Journée, whatever the Poste. */
    public long countParticipants(UUID eventId, LocalDate journee) {
        return count("registration.event.eventId = ?1 and journee = ?2 and waiting = false", eventId, journee);
    }

    /** Everyone with the Groupe at the Event that Journée. */
    public long countInGroup(UUID eventId, UUID groupId, LocalDate journee) {
        return count("registration.event.eventId = ?1 and registration.group.groupId = ?2 and journee = ?3", eventId, groupId, journee);
    }
}
