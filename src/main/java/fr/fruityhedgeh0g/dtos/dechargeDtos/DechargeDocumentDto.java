package fr.fruityhedgeh0g.dtos.dechargeDtos;

import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** A version of the décharge, without its content: downloaded from {@code /api/decharge/{documentId}/content}. */
public record DechargeDocumentDto(UUID documentId, String originalFilename, long fileSize, LocalDateTime uploadedAt) {
    public static DechargeDocumentDto of(DechargeDocumentEntity document) {
        return new DechargeDocumentDto(document.getDocumentId(), document.getOriginalFilename(), document.getFileSize(), document.getUploadedAt());
    }
}
