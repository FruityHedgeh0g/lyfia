package fr.fruityhedgeh0g.entities.configurations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import lombok.*;

@Builder
@Entity
@Table(name = "features")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FeatureEntity {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Attempts refused since the Feature was last turned off (the Journal shows it). The column default lets
     * Hibernate add it to a features table that already has rows (dev databases; Flyway's V8 does the same).
     */
    @ColumnDefault("0")
    @Column(name = "refused_count", nullable = false)
    private long refusedCount;
}
