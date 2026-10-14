package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.DemandeStatusEnum;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
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
    @Column(name = "poste", nullable = false)
    private PosteEnum poste;

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
        return poste == PosteEnum.PASSAGER;
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
        requireFits(requested);
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
        requireFits(placed);
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
     * The Bureau changes the Poste: never to passager, who names their conducteur; not for a conducteur who has
     * passagers. A passager leaves their conducteur; someone whose new Poste does not fit their Groupe's kind leaves
     * it, and a pending Demande for such a Groupe is dropped.
     */
    public void changePoste(PosteEnum changed) {
        if (changed == PosteEnum.PASSAGER)
            throw new InvalidResourceException("A passager names their conducteur when signing up.");
        if (!passagers.isEmpty() && changed != PosteEnum.CONDUCTEUR)
            throw new InvalidResourceException("A conducteur who has a passager keeps their Poste.");
        leaveConducteur();
        poste = changed;
        if (group != null && !changed.fits(group.getKind())) {
            group = null;
            demandeGroup = null;
            demandeStatus = null;
        } else if (hasPendingDemande() && !changed.fits(demandeGroup.getKind())) {
            demandeGroup = null;
            demandeStatus = null;
        }
    }

    /** A passager stops riding behind their conducteur. */
    private void leaveConducteur() {
        if (conducteur == null) return;
        conducteur.getPassagers().remove(this);
        conducteur = null;
    }

    /** Only people whose Poste fits its kind are with a Groupe; a Point fixe is with none. */
    private void requireFits(GroupEntity other) {
        if (!poste.fits(other.getKind()))
            throw new InvalidResourceException(poste == PosteEnum.POINT_FIXE ? "A point fixe is with no Groupe."
                    : "A " + poste.id() + " is with a Groupe de " + poste.groupKind().id() + " only.");
    }

    /**
     * The Chef rides with the Groupe they lead (ADR 0010): a confirmed place in it at the Poste of its kind, whatever the maximums.
     * A passager leaves their conducteur, a Demande is settled, and their own passagers follow.
     */
    public void leadGroup(GroupEntity led) {
        leaveConducteur();
        poste = led.getKind().chefPoste();
        moveUp();
        placeIn(led);
    }

    private void requirePendingDemande() {
        if (!hasPendingDemande())
            throw new InvalidResourceException("No pending Demande de groupe.");
    }

    /** A new conducteur sign-up, dated now; {@code waiting} puts it on the Liste d'attente. */
    public static EventRegistrationEntity conducteur(EventEntity event, UserEntity person, boolean waiting) {
        return signUp(event, person, PosteEnum.CONDUCTEUR, waiting);
    }

    /** A new sign-up at a Poste other than passager, dated now; {@code waiting} puts it on the Liste d'attente. */
    public static EventRegistrationEntity signUp(EventEntity event, UserEntity person, PosteEnum poste, boolean waiting) {
        return EventRegistrationEntity.builder()
                .event(event)
                .person(person)
                .poste(poste)
                .waiting(waiting)
                .signedUpAt(LocalDateTime.now(EventEntity.ZONE))
                .build();
    }

    /** A new passager sign-up with that conducteur, dated now, mirroring the conducteur's placement and Groupe. */
    public static EventRegistrationEntity passager(EventRegistrationEntity conducteur, UserEntity person) {
        if (conducteur.getPoste() != PosteEnum.CONDUCTEUR)
            throw new InvalidResourceException("A passager rides behind a conducteur.");
        EventRegistrationEntity passager = EventRegistrationEntity.builder()
                .event(conducteur.getEvent())
                .person(person)
                .poste(PosteEnum.PASSAGER)
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
