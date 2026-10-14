package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.dtos.dechargeDtos.DechargeDocumentDto;
import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.repositories.DechargeDocumentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The décharge de responsabilité a minor's legal representative approves when the minor signs up for an Event: the
 * Super admin uploads each version as a PDF; the latest is in force, and every version stays for the audit.
 */
@ApplicationScoped
public class DechargeService {

    @Inject
    DechargeDocumentRepository repository;

    /** The version in force, if any has been uploaded. */
    public Optional<DechargeDocumentDto> current() {
        return repository.findCurrent().map(DechargeDocumentDto::of);
    }

    /** The PDF of any version, the current or one already signed. */
    @Transactional
    public Optional<byte[]> content(UUID documentId) {
        return repository.findByIdOptional(documentId).map(DechargeDocumentEntity::getContent);
    }

    /** A new version, in force from now on; the previous ones are kept. */
    @Transactional
    public DechargeDocumentDto upload(String filename, String mimeType, byte[] content, UUID uploadedBy) {
        if (!DechargeDocumentEntity.MIME_TYPE.equals(mimeType) || !isPdf(content))
            throw new InvalidResourceException("The décharge is a PDF: " + mimeType);
        if (content.length > DechargeDocumentEntity.MAX_SIZE)
            throw new InvalidResourceException("The décharge weighs at most 8 MB: " + content.length + " bytes");
        DechargeDocumentEntity document = DechargeDocumentEntity.builder()
                .originalFilename(filename == null || filename.isBlank() ? "decharge.pdf" : filename)
                .fileSize(content.length)
                .uploadedAt(LocalDateTime.now(EventEntity.ZONE))
                .uploadedBy(uploadedBy)
                .content(content)
                .build();
        repository.persist(document);
        return DechargeDocumentDto.of(document);
    }

    /** A PDF starts with "%PDF-", whatever the browser said it was. */
    private static boolean isPdf(byte[] content) {
        return content.length > 5 && new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
    }
}
