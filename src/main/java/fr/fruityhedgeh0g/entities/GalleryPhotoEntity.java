package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.enums.VisibiliteEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Photo de la Galerie: a Media a Secteur puts in its Galerie, telling which Année it is of, and optionally which
 * Event and which of the Secteur's Groupes it shows; its Albums follow from that (ADR 0013).
 */
@Entity
@Table(name = "gallery_photos")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class GalleryPhotoEntity extends AuditTemplate {

    @Id
    @Column(name = "photo_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID photoId;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "media_id", nullable = false, unique = true)
    private MediaEntity media;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private SectorEntity sector;

    /** The Année of its Album: its Event's, or given by the Bureau when it has none. */
    @Column(name = "annee", nullable = false)
    private int annee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    private EventEntity event;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "gallery_photo_groups", joinColumns = @JoinColumn(name = "photo_id"), inverseJoinColumns = @JoinColumn(name = "group_id"))
    @Builder.Default
    private Set<GroupEntity> groups = new HashSet<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'BROUILLON'")
    @Column(name = "visibilite", nullable = false)
    private VisibiliteEnum visibilite = VisibiliteEnum.BROUILLON;

    /** Who deposited it: they see it while it is a Brouillon. */
    @Column(name = "deposited_by")
    private UUID depositedBy;

    /** The order of its Album after its Event. */
    @Column(name = "deposited_at", nullable = false)
    private LocalDateTime depositedAt;

    public boolean isOf(SectorEntity other) {
        return other != null && sector.getSectorId().equals(other.getSectorId());
    }
}
