package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PostEntity extends AuditTemplate {

    @Id
    @Column(name = "post_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    @NotNull
    private UUID postId;

    @Column(name = "title", nullable = false)
    @NotNull
    private String  title;

    /** An article: unbounded, unlike a varchar(255) (V2 migration). */
    @Column(name = "content", nullable = false, columnDefinition = "text")
    @NotNull
    private String content;

    /**
     * A new Post starts as Brouillon. The column default only fills Posts that existed before
     * statuses did: they were already public, so they stay Publié.
     */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PUBLIE'")
    @Column(name = "status", nullable = false)
    private PostStatusEnum status = PostStatusEnum.BROUILLON;

    /** The Bureau member who created the Post; unknown for Posts older than authors. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id")
    private UserEntity author;

    /**
     * The Secteur the Post belongs to: its author's (ADR 0004). None for a Post of the whole site, written by the
     * Super admin, or older than Secteurs.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private SectorEntity sector;

    public boolean isInClosedSector() {
        return sector != null && sector.isClosed();
    }

    public boolean isPublished() {
        return status == PostStatusEnum.PUBLIE;
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaEntity banner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "post_attachments", joinColumns = @JoinColumn(name = "post_id"), inverseJoinColumns = @JoinColumn(name = "media_id"))
    private List<MediaEntity> attachments;
}
