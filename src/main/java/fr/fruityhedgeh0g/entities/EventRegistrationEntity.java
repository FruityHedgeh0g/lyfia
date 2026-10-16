package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.DemandeStatusEnum;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's sign-up for an Event (ADR 0011): their Poste, Groupe and Demande de groupe, the same for the whole Event,
 * and the Journées they signed up for, each a confirmed place or a place on that Journée's Liste d'attente.
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

    /** When the person first signed up for the Event. */
    @Column(name = "signed_up_at", nullable = false)
    private LocalDateTime signedUpAt;

    /** The Groupe this person is with at this Event, once accepted or placed there. */
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

    /** For a minor: who approved the décharge (their legal representative), when, and which version. */
    @Column(name = "decharge_signataire")
    private String dechargeSignataire;

    @Column(name = "decharge_signed_at")
    private LocalDateTime dechargeSignedAt;

    @Column(name = "decharge_document_id")
    private UUID dechargeDocumentId;

    /** The Bureau member who recorded a décharge signed on paper, when the Bureau signed the minor up (ADR 0012). */
    @Column(name = "decharge_recorded_by")
    private UUID dechargeRecordedBy;

    /** The Journées signed up for; the sign-up goes once it has none left. */
    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("journee")
    @Builder.Default
    private List<JourneeRegistrationEntity> journees = new ArrayList<>();

    public boolean isPassager() {
        return poste == PosteEnum.PASSAGER;
    }

    public Optional<JourneeRegistrationEntity> journee(LocalDate journee) {
        return journees.stream().filter(j -> j.getJournee().equals(journee)).findFirst();
    }

    public JourneeRegistrationEntity journeeOrThrow(LocalDate journee) {
        return journee(journee).orElseThrow(() -> new InvalidResourceException("Not signed up for the Journée of " + journee));
    }

    public List<LocalDate> journeeDates() {
        return journees.stream().map(JourneeRegistrationEntity::getJournee).sorted().toList();
    }

    /** A new Journée, dated now; {@code waiting} puts it on that Journée's Liste d'attente. */
    public JourneeRegistrationEntity addJournee(LocalDate journee, boolean waiting) {
        if (journee(journee).isPresent())
            throw new InvalidResourceException("Already signed up for the Journée of " + journee);
        JourneeRegistrationEntity added = JourneeRegistrationEntity.builder()
                .registration(this)
                .journee(journee)
                .waiting(waiting)
                .signedUpAt(LocalDateTime.now(EventEntity.ZONE))
                .build();
        journees.add(added);
        journees.sort(Comparator.comparing(JourneeRegistrationEntity::getJournee));
        return added;
    }

    /** Leaves that Journée, ending its pairing. */
    public void removeJournee(JourneeRegistrationEntity journee) {
        journee.unpair();
        journees.remove(journee);
    }

    /** true while a passager rides behind this conducteur on any Journée. */
    public boolean hasPassager() {
        return journees.stream().anyMatch(j -> j.getPassager() != null);
    }

    /** Where the sign-up stands overall: on the Liste d'attente only while waiting on every Journée. */
    public RegistrationStatusEnum status() {
        return journees.stream().allMatch(JourneeRegistrationEntity::isWaiting) ? RegistrationStatusEnum.EN_ATTENTE
                : RegistrationStatusEnum.PARTICIPANT;
    }

    public boolean hasDecharge() {
        return dechargeSignedAt != null;
    }

    /**
     * The legal representative approves that version of the décharge, now; kept for the audit. {@code recordedBy}:
     * the Bureau member recording it, signed on paper, or null when approved online.
     */
    public void approveDecharge(String signataire, UUID documentId, UUID recordedBy) {
        dechargeSignataire = signataire.trim();
        dechargeSignedAt = LocalDateTime.now(EventEntity.ZONE);
        dechargeDocumentId = documentId;
        dechargeRecordedBy = recordedBy;
    }

    /** A new pending Demande de groupe; only someone outside any Groupe at this Event makes one. */
    public void requestGroup(GroupEntity requested) {
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
    }

    /** Takes the person out of their Groupe; they remain signed up for the Event. */
    public void leaveGroup() {
        if (group == null)
            throw new InvalidResourceException("Not riding with a Groupe at this Event.");
        group = null;
        demandeGroup = null;
        demandeStatus = null;
    }

    /** Ends every pairing of this sign-up, as passager or as conducteur. */
    public void unpairAll() {
        journees.forEach(JourneeRegistrationEntity::unpair);
    }

    /** true while this person leads the Groupe they ride with: their sign-up follows the Affectation alone (ADR 0010). */
    public boolean isChefOfItsGroup() {
        return group != null && group.getChef() != null && group.getChef().getUserId().equals(person.getUserId());
    }

    /**
     * The Bureau changes the Poste: not for a conducteur who has a passager. A passager becoming something else is no
     * longer paired; someone whose new Poste does not fit their Groupe's kind leaves it, and a pending Demande for
     * such a Groupe is dropped.
     */
    public void changePoste(PosteEnum changed) {
        if (hasPassager() && changed != PosteEnum.CONDUCTEUR)
            throw new InvalidResourceException("A conducteur who has a passager keeps their Poste.");
        if (changed != PosteEnum.PASSAGER) unpairAll();
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

    /** Only people whose Poste fits its kind are with a Groupe; a Point fixe is with none. */
    private void requireFits(GroupEntity other) {
        if (!poste.fits(other.getKind()))
            throw new InvalidResourceException(poste == PosteEnum.POINT_FIXE ? "A point fixe is with no Groupe."
                    : "A " + poste.id() + " is with a Groupe de " + poste.groupKind().id() + " only.");
    }

    /**
     * The Chef is at every Journée of the Event with the Groupe they lead (ADR 0010): a confirmed place at the Poste of
     * its kind, whatever the maximums. A passager is no longer paired, nor is a Chef who stops being a conducteur, and
     * a Demande is settled.
     */
    public void leadGroup(GroupEntity led, List<LocalDate> eventJournees) {
        if (isPassager() || led.getKind().chefPoste() != PosteEnum.CONDUCTEUR) unpairAll();
        poste = led.getKind().chefPoste();
        eventJournees.forEach(d -> journee(d).ifPresentOrElse(j -> j.setWaiting(false), () -> addJournee(d, false)));
        placeIn(led);
    }

    private void requirePendingDemande() {
        if (!hasPendingDemande())
            throw new InvalidResourceException("No pending Demande de groupe.");
    }

    /** A new sign-up at a Poste, dated now, without any Journée yet. */
    public static EventRegistrationEntity of(EventEntity event, UserEntity person, PosteEnum poste) {
        return EventRegistrationEntity.builder()
                .event(event)
                .person(person)
                .poste(poste)
                .signedUpAt(LocalDateTime.now(EventEntity.ZONE))
                .build();
    }

    /** A new sign-up at a Poste for every Journée of the Event; {@code waiting} puts it on their Listes d'attente. */
    public static EventRegistrationEntity signUp(EventEntity event, UserEntity person, PosteEnum poste, boolean waiting) {
        EventRegistrationEntity registration = of(event, person, poste);
        event.journees().forEach(d -> registration.addJournee(d, waiting));
        return registration;
    }

    /** A new conducteur sign-up for every Journée of the Event. */
    public static EventRegistrationEntity conducteur(EventEntity event, UserEntity person, boolean waiting) {
        return signUp(event, person, PosteEnum.CONDUCTEUR, waiting);
    }

    /** A new passager sign-up behind that conducteur on each of their Journées, in their Groupe, waiting as they do. */
    public static EventRegistrationEntity passager(EventRegistrationEntity conducteur, UserEntity person) {
        if (conducteur.getPoste() != PosteEnum.CONDUCTEUR)
            throw new InvalidResourceException("A passager rides behind a conducteur.");
        EventRegistrationEntity passager = of(conducteur.getEvent(), person, PosteEnum.PASSAGER);
        passager.setGroup(conducteur.getGroup());
        conducteur.getJournees().forEach(j -> passager.addJournee(j.getJournee(), j.isWaiting()).pairWith(j));
        return passager;
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
