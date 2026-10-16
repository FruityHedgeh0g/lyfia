package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakRoleMirror;
import fr.fruityhedgeh0g.repositories.DechargeDocumentRepository;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
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
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bureau adds a person to an Event (ADR 0012): an Inscrit, or a Bénévole sans compte recorded on the way, under
 * the rules of any sign-up but the phone number; a minor's décharge, signed on paper, is recorded by the Bureau.
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class BenevoleSansCompteResourceTest {

    static final String ADULT_ID = "00000000-0000-0000-0016-000000000001";
    static final String BUREAU_ID = "00000000-0000-0000-0016-000000000002";
    static final String BENEVOLE_ID = "00000000-0000-0000-0016-000000000003";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0016-000000000004";
    /** The last name of every Bénévole sans compte the tests record, to clean them up. */
    static final String SANS_COMPTE = "SansCompteTest";
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

    @Inject
    FeatureRepository featureRepository;

    @Inject
    FakeKeycloakRoleMirror keycloak;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;
    private UUID event;
    private LocalDate eventDay;

    @BeforeEach
    void seed() {
        eventDay = LocalDate.now(EventEntity.ZONE).plusDays(10);
        persistPerson(ADULT_ID, RoleEnum.BENEVOLE);
        persistPerson(BUREAU_ID, RoleEnum.BUREAU);
        persistPerson(BENEVOLE_ID, RoleEnum.BENEVOLE);
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN);
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
        keycloak.reset();
    }

    @AfterEach
    void cleanUp() {
        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, true);
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            dechargeRepository.deleteAll();
            sectorRepository.deleteById(sector);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            persons.forEach(userRepository::deleteById);
            userRepository.delete("lastName", SANS_COMPTE);
        });
        persons.clear();
    }

    private void persistPerson(String id, RoleEnum role) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(UserEntity.builder().userId(userId)
                .firstName("Test").lastName(role.name()).role(role).phone("06 00 00 00 00").birthDate(LocalDate.of(1990, 1, 1)).build()));
        persons.add(userId);
    }

    private void turn(FeatureEnum feature, boolean on) {
        QuarkusTransaction.requiringNew().run(() -> {
            FeatureEntity existing = featureRepository.findById(feature.id());
            if (existing == null) featureRepository.persist(FeatureEntity.builder().name(feature.id()).description("Test").isActive(on).build());
            else existing.setIsActive(on);
        });
    }

    private void dechargeUploaded() {
        QuarkusTransaction.requiringNew().run(() -> dechargeRepository.persist(DechargeDocumentEntity.builder()
                .originalFilename("decharge.pdf").fileSize(PDF.length).uploadedAt(LocalDateTime.now())
                .uploadedBy(UUID.fromString(SUPER_ADMIN_ID)).content(PDF).build()));
    }

    private static Map<String, Object> newPerson(String firstName, LocalDate birthDate, String phone) {
        Map<String, Object> person = new HashMap<>();
        person.put("firstName", firstName);
        person.put("lastName", SANS_COMPTE);
        person.put("birthDate", birthDate.toString());
        if (phone != null) person.put("phone", phone);
        return person;
    }

    private ValidatableResponse add(Map<String, Object> addition) {
        return given().contentType(ContentType.JSON).body(addition).when().post("/{id}/roster", event).then();
    }

    private List<UserEntity> sansCompte() {
        return QuarkusTransaction.requiringNew().call(() -> userRepository.list("lastName", SANS_COMPTE));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauAddsABenevoleSansCompteWithoutAPhone() {
        add(Map.of("newPerson", newPerson("Marcel", LocalDate.of(1950, 3, 4), null), "poste", "point_fixe"))
                .statusCode(200)
                .body("participants.lastName", hasItem(SANS_COMPTE))
                .body("participants.find { it.lastName == '" + SANS_COMPTE + "' }.withoutAccount", equalTo(true))
                .body("participants.find { it.lastName == '" + SANS_COMPTE + "' }.phone", nullValue());

        List<UserEntity> recorded = sansCompte();
        assertEquals(1, recorded.size());
        assertTrue(recorded.getFirst().isWithoutAccount());
        assertEquals(RoleEnum.BENEVOLE, recorded.getFirst().getRole());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauAddsAnInscritWhoCalledIt() {
        add(Map.of("personId", ADULT_ID, "poste", "conducteur")).statusCode(200)
                .body("participants.find { it.personId == '" + ADULT_ID + "' }.withoutAccount", equalTo(false));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void eitherAnInscritOrANewPerson() {
        add(Map.of("poste", "conducteur")).statusCode(400);
        add(Map.of("personId", ADULT_ID, "newPerson", newPerson("Marcel", LocalDate.of(1950, 3, 4), null), "poste", "conducteur"))
                .statusCode(400);
        assertTrue(sansCompte().isEmpty());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aMinorsPaperDechargeIsRecordedWithTheBureauMember() {
        dechargeUploaded();
        LocalDate minor = eventDay.minusYears(15);
        add(Map.of("newPerson", newPerson("Lucas", minor, null), "poste", "passager")).statusCode(422)
                .body("error", equalTo("decharge-requise"));
        // A refused addition records nobody
        assertTrue(sansCompte().isEmpty());

        add(Map.of("newPerson", newPerson("Lucas", minor, null), "poste", "passager", "dechargeSignataire", "Parent Test"))
                .statusCode(200)
                .body("participants.find { it.lastName == '" + SANS_COMPTE + "' }.decharge.signataire", equalTo("Parent Test"))
                .body("participants.find { it.lastName == '" + SANS_COMPTE + "' }.decharge.recordedBy", equalTo(BUREAU_ID));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void inscriptionOffRefusesTheBureauToo() {
        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, false);
        add(Map.of("newPerson", newPerson("Marcel", LocalDate.of(1950, 3, 4), null), "poste", "point_fixe"))
                .statusCode(503);
        assertTrue(sansCompte().isEmpty());
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE_ID))
    void onlyTheBureauAdds() {
        add(Map.of("personId", ADULT_ID, "poste", "conducteur")).statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauKeepsTheDetailsOfABenevoleSansCompteOnly() {
        add(Map.of("newPerson", newPerson("Marcel", LocalDate.of(1950, 3, 4), null), "poste", "point_fixe")).statusCode(200);
        UUID marcel = sansCompte().getFirst().getUserId();

        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("phone", "03 82 00 00 00", "birthDate", "1951-03-04"))
                .when().patch("/users/{id}/coordonnees", marcel).then().statusCode(200)
                .body("phone", equalTo("03 82 00 00 00"))
                .body("birthDate", equalTo("1951-03-04"));
        // Someone with an account keeps their own, in Mon profil
        add(Map.of("personId", ADULT_ID, "poste", "conducteur")).statusCode(200);
        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("phone", "03 82 00 00 00"))
                .when().patch("/users/{id}/coordonnees", ADULT_ID).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aBenevoleSansCompteIsPromotedWithoutKeycloak() {
        add(Map.of("newPerson", newPerson("Marcel", LocalDate.of(1950, 3, 4), null), "poste", "point_fixe")).statusCode(200);
        UUID marcel = sansCompte().getFirst().getUserId();

        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("role", "membre"))
                .when().put("/users/{id}/role", marcel).then().statusCode(200)
                .body("role", equalTo("membre"));
        assertFalse(keycloak.calls().stream().anyMatch(call -> call.personId().equals(marcel)));
    }
}
