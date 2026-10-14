package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.DemandeStatusEnum;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.enums.RideModeEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A person's sign-up for an Event: a confirmed place (Participant) or a place
 * on the Event's Liste d'attente, ordered by sign-up time. A passager's sign-up
 * points at their conducteur's and follows every change to its placement and Groupe.
 */
@Entity
@Table(name = "event_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "person_id"}))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class EventRegistrationEntity extends AuditTemplate {

    @Id
    @Column(name = "registration_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID registrationId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private EventEntity event;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private UserEntity person;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private RideModeEnum mode;

    /** true while on the Liste d'attente; nobody moves up automatically. */
    @Column(name = "waiting", nullable = false)
    private boolean waiting;

    @Column(name = "signed_up_at", nullable = false)
    private LocalDateTime signedUpAt;

    /** The Groupe this person rides with at this Event, once accepted or placed there. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private GroupEntity group;

    /** The Groupe asked for by the latest Demande de groupe, if any. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "demande_group_id")
    private GroupEntity demandeGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "demande_status")
    private DemandeStatusEnum demandeStatus;

    /** The conducteur's sign-up a passager rides with; null for a conducteur. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conducteur_registration_id")
    private EventRegistrationEntity conducteur;

    /**
     * A conducteur's passagers; removing the conducteur's sign-up (through the entity manager) removes theirs.
     * A bulk JPQL delete bypasses this cascade: delete passagers first.
     */
    @OneToMany(mappedBy = "conducteur", cascade = CascadeType.REMOVE)
    @Builder.Default
    private List<EventRegistrationEntity> passagers = new ArrayList<>();

    public boolean isPassager() {
        return mode == RideModeEnum.PASSAGER;
    }

    /** Places this sign-up takes towards a maximum: the conducteur and their passagers. */
    public int placesTaken() {
        return 1 + passagers.size();
    }

    /** Moves the conducteur and their passagers up from the Liste d'attente. */
    public void moveUp() {
        waiting = false;
        passagers.forEach(p -> p.waiting = false);
    }

    private void passagersFollowGroup() {
        passagers.forEach(p -> p.group = group);
    }

    /** A new pending Demande de groupe; only someone outside any Groupe at this Event makes one. */
    public void requestGroup(GroupEntity requested) {
        if (isPassager())
            throw new InvalidResourceException("A passager rides with their conducteur's Groupe.");
        if (group != null)
            throw new InvalidResourceException("Already riding with a Groupe at this Event.");
        demandeGroup = requested;
        demandeStatus = DemandeStatusEnum.EN_ATTENTE;
    }

    public boolean hasPendingDemande() {
        return demandeStatus == DemandeStatusEnum.EN_ATTENTE;
    }

    /** true once accepted in, or placed in, that Groupe at this Event. */
    public boolean ridesWith(GroupEntity other) {
        return group != null && group.getGroupId().equals(other.getGroupId());
    }

    /** true while a pending Demande for that Groupe waits for a decision (its Liste d'attente). */
    public boolean asksFor(GroupEntity other) {
        return hasPendingDemande() && demandeGroup.getGroupId().equals(other.getGroupId());
    }

    public void acceptDemande() {
        requirePendingDemande();
        group = demandeGroup;
        demandeStatus = DemandeStatusEnum.ACCEPTEE;
        passagersFollowGroup();
    }

    /** The person stays signed up, without a Groupe, and may make a new Demande. */
    public void refuseDemande() {
        requirePendingDemande();
        demandeStatus = DemandeStatusEnum.REFUSEE;
    }

    /** The Bureau places the person in a Groupe directly, settling any Demande. */
    public void placeIn(GroupEntity placed) {
        group = placed;
        demandeGroup = placed;
        demandeStatus = DemandeStatusEnum.ACCEPTEE;
        passagersFollowGroup();
    }

    /** Takes the person out of their Groupe; they remain signed up for the Event. */
    public void leaveGroup() {
        if (group == null)
            throw new InvalidResourceException("Not riding with a Groupe at this Event.");
        group = null;
        demandeGroup = null;
        demandeStatus = null;
        passagersFollowGroup();
    }

    /** true while this person leads the Groupe they ride with: their sign-up follows the Affectation alone (ADR 0010). */
    public boolean isChefOfItsGroup() {
        return group != null && group.getChef() != null && group.getChef().getUserId().equals(person.getUserId());
    }

    /**
     * The Chef rides with the Groupe they lead (ADR 0010): a confirmed conducteur place in it, whatever the maximums.
     * A passager leaves their conducteur, a Demande is settled, and their own passagers follow.
     */
    public void leadGroup(GroupEntity led) {
        if (isPassager()) {
            conducteur.getPassagers().remove(this);
            conducteur = null;
            mode = RideModeEnum.CONDUCTEUR;
        }
        moveUp();
        placeIn(led);
    }

    private void requirePendingDemande() {
        if (!hasPendingDemande())
            throw new InvalidResourceException("No pending Demande de groupe.");
    }

    /** A new conducteur sign-up, dated now; {@code waiting} puts it on the Liste d'attente. */
    public static EventRegistrationEntity conducteur(EventEntity event, UserEntity person, boolean waiting) {
        return EventRegistrationEntity.builder()
                .event(event)
                .person(person)
                .mode(RideModeEnum.CONDUCTEUR)
                .waiting(waiting)
                .signedUpAt(LocalDateTime.now(EventEntity.ZONE))
                .build();
    }

    /** A new passager sign-up with that conducteur, dated now, mirroring the conducteur's placement and Groupe. */
    public static EventRegistrationEntity passager(EventRegistrationEntity conducteur, UserEntity person) {
        if (conducteur.isPassager())
            throw new InvalidResourceException("A passager rides with a conducteur, not with another passager.");
        EventRegistrationEntity passager = EventRegistrationEntity.builder()
                .event(conducteur.getEvent())
                .person(person)
                .mode(RideModeEnum.PASSAGER)
                .conducteur(conducteur)
                .waiting(conducteur.isWaiting())
                .group(conducteur.getGroup())
                .signedUpAt(LocalDateTime.now(EventEntity.ZONE))
                .build();
        conducteur.getPassagers().add(passager);
        return passager;
    }

    public RegistrationStatusEnum status() {
        return waiting ? RegistrationStatusEnum.EN_ATTENTE : RegistrationStatusEnum.PARTICIPANT;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(registrationId, ((EventRegistrationEntity) o).registrationId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(registrationId);
    }
}
