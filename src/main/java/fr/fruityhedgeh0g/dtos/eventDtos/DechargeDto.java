package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.entities.EventRegistrationEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** The décharge approved for a minor's sign-up: by whom, when, and which version of the document. */
public record DechargeDto(String signataire, LocalDateTime signedAt, UUID documentId) {
    public static DechargeDto of(EventRegistrationEntity registration) {
        return registration.getDechargeSignedAt() == null ? null
                : new DechargeDto(registration.getDechargeSignataire(), registration.getDechargeSignedAt(), registration.getDechargeDocumentId());
    }
}
