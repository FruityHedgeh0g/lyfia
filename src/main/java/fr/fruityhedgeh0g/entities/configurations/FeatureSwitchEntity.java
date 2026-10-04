package fr.fruityhedgeh0g.entities.configurations;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry of the Journal: a Feature turned off or on, by whom, when, for the whole site or one Secteur, and why.
 * Never changed nor deleted from the site; purged by hand in the database.
 */
@Entity
@Table(name = "feature_switches")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FeatureSwitchEntity {

    @Id
    @Column(name = "switch_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID switchId;

    @Column(name = "feature", nullable = false)
    private String feature;

    /** Null: the lever for the whole site. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private SectorEntity sector;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "switched_by")
    private UserEntity switchedBy;

    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt;
}
