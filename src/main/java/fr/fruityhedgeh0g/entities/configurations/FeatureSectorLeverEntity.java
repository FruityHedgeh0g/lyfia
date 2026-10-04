package fr.fruityhedgeh0g.entities.configurations;

import fr.fruityhedgeh0g.entities.SectorEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A Feature's lever for one Secteur (ADR 0009), independent of its lever for the whole site. A Secteur without one
 * has it on, so a new Secteur starts with every lever on.
 */
@Entity
@Table(name = "feature_sector_levers", uniqueConstraints = @UniqueConstraint(columnNames = {"feature", "sector_id"}))
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FeatureSectorLeverEntity {

    @Id
    @Column(name = "lever_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID leverId;

    @Column(name = "feature", nullable = false)
    private String feature;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private SectorEntity sector;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
