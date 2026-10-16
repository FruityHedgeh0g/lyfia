package fr.fruityhedgeh0g.entities.medias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A file of the database storage (ADR 0014), by file key, apart from the Media it belongs to, so listing Medias
 * never loads it.
 */
@Entity
@Table(name = "media_files")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class MediaFileEntity {

    /** The largest file the database storage takes, whatever the Super admin allows (ADR 0008). */
    public static final int MAX_SIZE = 20 * 1024 * 1024;

    @Id
    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    /** bytea in PostgreSQL. */
    @Column(name = "content", nullable = false, length = MediaFileEntity.MAX_SIZE)
    private byte[] content;
}
