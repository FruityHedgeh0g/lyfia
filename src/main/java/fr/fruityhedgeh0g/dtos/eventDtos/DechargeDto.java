package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.entities.EventRegistrationEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The décharge approved for a minor's sign-up: by whom, when, and which version of the document; {@code recordedBy}
 * the Bureau member who recorded it, signed on paper (ADR 0012).
 */
public record DechargeDto(String signataire, LocalDateTime signedAt, UUID documentId, UUID recordedBy) {
    public static DechargeDto of(EventRegistrationEntity registration) {
        return registration.getDechargeSignedAt() == null ? null
                : new DechargeDto(registration.getDechargeSignataire(), registration.getDechargeSignedAt(),
                registration.getDechargeDocumentId(), registration.getDechargeRecordedBy());
    }
}
