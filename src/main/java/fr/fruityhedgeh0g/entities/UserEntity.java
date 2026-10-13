package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class UserEntity extends AuditTemplate{

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Required before signing up for an Event. */
    @Column(name = "phone")
    private String phone;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'BENEVOLE'")
    @Column(name = "role", nullable = false)
    private RoleEnum role = RoleEnum.BENEVOLE;

    /**
     * Membre du Bureau or Président while sitting on the Secteur's Bureau (ADR 0001): always with the Bureau Role,
     * by choice for an Admin; none below the Bureau Role. Grants no access.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bureau_function")
    private BureauFunctionEnum bureauFunction;

    /**
     * The Secteur this person belongs to, from Membre up (ADR 0004); none for a Bénévole
     * (the pool shared by every Secteur) or the Super admin (above all Secteurs).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private SectorEntity sector;

    public boolean belongsTo(SectorEntity other) {
        return sector != null && other != null && sector.getSectorId().equals(other.getSectorId());
    }

    /** The Bureau Role always sits on the Bureau, as Membre du Bureau unless given another Fonction. */
    public BureauFunctionEnum getBureauFunction() {
        return role == RoleEnum.BUREAU && bureauFunction == null ? BureauFunctionEnum.MEMBRE_DU_BUREAU : bureauFunction;
    }

    public boolean isPresident() {
        return getBureauFunction() == BureauFunctionEnum.PRESIDENT;
    }

    /**
     * Changes the Role (ADR 0001): the Fonction au Bureau is kept between Bureau and Admin, given on reaching the
     * Bureau Role, and lost below it; the Super admin, above every Secteur, sits on no Bureau.
     */
    public void changeRole(RoleEnum role) {
        this.role = role;
        if (!role.isAtLeast(RoleEnum.BUREAU) || role == RoleEnum.SUPER_ADMIN)
            this.bureauFunction = null;
        else if (role == RoleEnum.BUREAU && this.bureauFunction == null)
            this.bureauFunction = BureauFunctionEnum.MEMBRE_DU_BUREAU;
    }

    /** A phone number is required before signing up for an Event. */
    public boolean hasPhone() {
        return phone != null && !phone.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserEntity that = (UserEntity) o;
        return Objects.equals(userId, that.userId) && Objects.equals(firstName, that.firstName) && Objects.equals(lastName, that.lastName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
