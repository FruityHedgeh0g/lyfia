package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.DechargeDocumentRepository;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * A person's e-mail (their Keycloak account's) and birth date; the birth date is required to sign up for an Event,
 * and a minor's sign-up needs the décharge in force approved by their legal representative, kept for the audit.
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class ProfileAndDechargeResourceTest {

    static final String ADULT_ID = "00000000-0000-0000-0015-000000000001";
    static final String MINOR_ID = "00000000-0000-0000-0015-000000000002";
    static final String NO_BIRTH_DATE_ID = "00000000-0000-0000-0015-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0015-000000000004";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0015-000000000005";
    static final byte[] PDF = "%PDF-1.4 décharge".getBytes(StandardCharsets.UTF_8);

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    @Inject
    DechargeDocumentRepository dechargeRepository;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;
    private UUID event;

    @BeforeEach
    void seed() {
        LocalDate eventDay = LocalDate.now(EventEntity.ZONE).plusDays(10);
        persistPerson(ADULT_ID, RoleEnum.BENEVOLE, LocalDate.of(1990, 1, 1));
        // 18 the day after the Event: still a minor on its Journée
        persistPerson(MINOR_ID, RoleEnum.BENEVOLE, eventDay.minusYears(18).plusDays(1));
        persistPerson(NO_BIRTH_DATE_ID, RoleEnum.BENEVOLE, null);
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, LocalDate.of(1980, 1, 1));
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN, LocalDate.of(1970, 1, 1));
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(eventDay.atTime(9, 0));
            e.setEndDateTime(eventDay.atTime(18, 0));
            e.setSector(s);
            eventRepository.persist(e);
            event = e.getEventId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            dechargeRepository.deleteAll();
            sectorRepository.deleteById(sector);
        });
        QuarkusTransaction.requiringNew().run(() -> persons.forEach(userRepository::deleteById));
        persons.clear();
    }

    private void persistPerson(String id, RoleEnum role, LocalDate birthDate) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(UserEntity.builder().userId(userId)
                .firstName("Test").lastName(role.name()).role(role).phone("06 00 00 00 00").birthDate(birthDate).build()));
        persons.add(userId);
    }

    private UUID dechargeUploaded() {
        return QuarkusTransaction.requiringNew().call(() -> {
            DechargeDocumentEntity document = DechargeDocumentEntity.builder().originalFilename("decharge.pdf")
                    .fileSize(PDF.length).uploadedAt(LocalDateTime.now()).uploadedBy(UUID.fromString(SUPER_ADMIN_ID)).content(PDF).build();
            dechargeRepository.persist(document);
            return document.getDocumentId();
        });
    }

    // --- The profile ---

    @Test
    @TestSecurity(user = "adult", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = {@Claim(key = "sub", value = ADULT_ID), @Claim(key = "email", value = "adulte@example.org")})
    void theEmailIsTheKeycloakAccountsAndTheBirthDateTheirOwn() {
        given().basePath("/api").when().get("/users/me").then().statusCode(200)
                .body("email", equalTo("adulte@example.org"))
                .body("birthDate", equalTo("1990-01-01"));

        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("phone", "06 11 11 11 11", "birthDate", "1985-06-15"))
                .when().patch("/users/me").then().statusCode(200)
                .body("birthDate", equalTo("1985-06-15"));
        // Left out, the birth date is kept
        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("phone", "06 22 22 22 22"))
                .when().patch("/users/me").then().statusCode(200)
                .body("birthDate", equalTo("1985-06-15"));
        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("birthDate", LocalDate.now().plusDays(1).toString()))
                .when().patch("/users/me").then().statusCode(400);
    }

    // --- Signing up ---

    @Test
    @TestSecurity(user = "unknown", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = NO_BIRTH_DATE_ID))
    void theBirthDateIsRequiredToSignUp() {
        given().when().put("/{id}/registration", event).then().statusCode(422).body("error", equalTo("birth-date-required"));
    }

    @Test
    @TestSecurity(user = "adult", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADULT_ID))
    void anAdultSignsUpWithoutDecharge() {
        given().when().put("/{id}/registration", event).then().statusCode(200).body("decharge", nullValue());
    }

    @Test
    @TestSecurity(user = "minor", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MINOR_ID))
    void minorsCannotSignUpBeforeADechargeIsUploaded() {
        given().queryParam("signataire", "Parent Test").when().put("/{id}/registration", event).then().statusCode(422)
                .body("error", equalTo("decharge-indisponible"));
    }

    @Test
    @TestSecurity(user = "minor", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MINOR_ID))
    void aMinorsLegalRepresentativeApprovesTheDechargeInForce() {
        UUID document = dechargeUploaded();
        given().when().put("/{id}/registration", event).then().statusCode(422).body("error", equalTo("decharge-requise"));

        given().queryParam("signataire", "  Parent Test ").when().put("/{id}/registration", event).then().statusCode(200)
                .body("decharge.signataire", equalTo("Parent Test"))
                .body("decharge.signedAt", notNullValue())
                .body("decharge.documentId", equalTo(document.toString()));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSeesTheEmailBirthDateAndDecharge() {
        UUID document = dechargeUploaded();
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity minor = userRepository.findById(UUID.fromString(MINOR_ID));
            minor.setEmail("mineur@example.org");
            var registration = fr.fruityhedgeh0g.entities.EventRegistrationEntity.conducteur(eventRepository.findById(event), minor, false);
            registration.approveDecharge("Parent Test", document);
            registrationRepository.persist(registration);
        });

        given().when().get("/{id}/roster", event).then().statusCode(200)
                .body("participants[0].email", equalTo("mineur@example.org"))
                .body("participants[0].birthDate", notNullValue())
                .body("participants[0].decharge.signataire", equalTo("Parent Test"));
    }

    // --- The document ---

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminUploadsAPdfAsTheNewVersion() {
        given().basePath("/api").when().get("/decharge").then().statusCode(204);
        given().basePath("/api").multiPart("file", "decharge.txt", "pas un pdf".getBytes(StandardCharsets.UTF_8), "text/plain")
                .when().post("/decharge").then().statusCode(400);

        String first = given().basePath("/api").multiPart("file", "decharge-v1.pdf", PDF, "application/pdf")
                .when().post("/decharge").then().statusCode(200).extract().path("documentId");
        String second = given().basePath("/api").multiPart("file", "decharge-v2.pdf", PDF, "application/pdf")
                .when().post("/decharge").then().statusCode(200).extract().path("documentId");

        given().basePath("/api").when().get("/decharge").then().statusCode(200)
                .body("documentId", equalTo(second)).body("originalFilename", equalTo("decharge-v2.pdf"));
        // Every version stays, for the audit
        given().basePath("/api").when().get("/decharge/{id}/content", first).then().statusCode(200).contentType("application/pdf");
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void onlyTheSuperAdminUploadsTheDecharge() {
        given().basePath("/api").multiPart("file", "decharge.pdf", PDF, "application/pdf")
                .when().post("/decharge").then().statusCode(403);
    }
}
