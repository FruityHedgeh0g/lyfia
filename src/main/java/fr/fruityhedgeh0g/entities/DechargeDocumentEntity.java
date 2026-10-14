package fr.fruityhedgeh0g.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One version of the décharge de responsabilité a minor's legal representative approves when the minor signs up for
 * an Event: a PDF the Super admin uploads. A new upload is a new version; none is ever replaced, so that each signed
 * décharge keeps the very text approved.
 */
@Entity
@Table(name = "decharge_documents")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class DechargeDocumentEntity {

    public static final int MAX_SIZE = 8 * 1024 * 1024;
    public static final String MIME_TYPE = "application/pdf";

    @Id
    @Column(name = "document_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID documentId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /** The PDF, loaded only when downloaded (bytea in PostgreSQL). */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, length = MAX_SIZE)
    private byte[] content;
}
