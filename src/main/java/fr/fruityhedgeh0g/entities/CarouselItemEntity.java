package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A slide of the home page's carousel, in the order the Bureau sets. */
@Entity
@Table(name = "carousel_items")
@NoArgsConstructor
@Getter
@Setter
public class CarouselItemEntity extends AuditTemplate {

    @Id
    @Column(name = "item_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID itemId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "caption", columnDefinition = "text")
    private String caption;

    /** None: the association's logo. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "media_id")
    private MediaEntity media;

    /** A path of the site, such as /evenements. */
    @Column(name = "link_to")
    private String linkTo;

    /** A slide can be put aside without being deleted. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "position", nullable = false)
    private int position;

    /** The Secteur whose Bureau manages the slide; none for the whole site, managed by the Super admin. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private SectorEntity sector;

    public boolean isInClosedSector() {
        return sector != null && sector.isClosed();
    }
}
