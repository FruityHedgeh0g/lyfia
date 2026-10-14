package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A sign-up for one Journée of an Event (ADR 0011): a confirmed place or a place on that Journée's Liste d'attente,
 * ordered by when it was taken, and, for a passager, the conducteur they ride behind that Journée.
 */
@Entity
@Table(name = "journee_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"registration_id", "journee"}))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class JourneeRegistrationEntity extends AuditTemplate {

    @Id
    @Column(name = "journee_registration_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID journeeRegistrationId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private EventRegistrationEntity registration;

    @Column(name = "journee", nullable = false)
    private LocalDate journee;

    /** true while on that Journée's Liste d'attente; nobody moves up automatically. */
    @Column(name = "waiting", nullable = false)
    private boolean waiting;

    /** When this Journée was signed up for: the order of its Liste d'attente. */
    @Column(name = "signed_up_at", nullable = false)
    private LocalDateTime signedUpAt;

    /** For a passager: the conducteur's sign-up for the same Journée; a conducteur takes at most one passager. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conducteur_journee_id", unique = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private JourneeRegistrationEntity conducteur;

    /** For a conducteur: the passager riding behind them that Journée, if any. */
    @OneToOne(mappedBy = "conducteur", fetch = FetchType.EAGER)
    private JourneeRegistrationEntity passager;

    /** The conducteur's sign-up and this one ride together this Journée: they wait and move up together. */
    public void pairWith(JourneeRegistrationEntity conducteurJournee) {
        if (conducteurJournee.passager != null && !conducteurJournee.passager.equals(this))
            throw new InvalidResourceException("A conducteur takes at most one passager per Journée.");
        unpair();
        conducteur = conducteurJournee;
        conducteurJournee.passager = this;
    }

    /** Ends the pairing this sign-up is part of, as passager or as conducteur. */
    public void unpair() {
        if (conducteur != null) {
            conducteur.passager = null;
            conducteur = null;
        }
        if (passager != null) {
            passager.conducteur = null;
            passager = null;
        }
    }

    /** The other one of the pair this Journée, if any. */
    public JourneeRegistrationEntity partner() {
        return conducteur != null ? conducteur : passager;
    }

    public RegistrationStatusEnum status() {
        return waiting ? RegistrationStatusEnum.EN_ATTENTE : RegistrationStatusEnum.PARTICIPANT;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(journeeRegistrationId, ((JourneeRegistrationEntity) o).journeeRegistrationId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(journeeRegistrationId);
    }
}
