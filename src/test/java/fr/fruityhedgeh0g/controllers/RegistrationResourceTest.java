package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
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
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Signing up for an Event as conducteur, the Event Liste d'attente, withdrawal, and the phone number it needs. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class RegistrationResourceTest {

    static final String ME = "00000000-0000-0000-0005-000000000002";
    static final String NO_PHONE = "00000000-0000-0000-0005-000000000012";
    static final String BUREAU_ID = "00000000-0000-0000-0005-000000000005";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;

    @BeforeEach
    void seed() {
        persistPerson(UUID.fromString(ME), RoleEnum.BENEVOLE, "06 12 34 56 78");
        persistPerson(UUID.fromString(NO_PHONE), RoleEnum.BENEVOLE, null);
        persistPerson(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU, "06 00 00 00 00");
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            sectorRepository.deleteById(sector);
            persons.forEach(userRepository::deleteById);
        });
        persons.clear();
    }

    private UUID persistPerson(UUID id, RoleEnum role, String phone) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(id).firstName("Test").lastName(role.name()).role(role).phone(phone).build()
        ));
        persons.add(id);
        return id;
    }

    /** Stores an Event with dates relative to now (in hours) and an optional maximum. */
    private UUID persistEvent(EventStatusEnum stored, long startInHours, long endInHours, Integer max) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity event = new EventEntity();
            event.setName("Test " + stored);
            event.setStatus(stored);
            event.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusHours(startInHours));
            event.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusHours(endInHours));
            event.setMaxParticipants(max);
            event.setSector(sectorRepository.findById(sector));
            eventRepository.persist(event);
            return event.getEventId();
        });
    }

    private UUID futureEvent(EventStatusEnum stored, Integer max) {
        return persistEvent(stored, 24, 48, max);
    }

    /** Fills an Event with confirmed places taken by other people. */
    private void fill(UUID eventId, int count) {
        for (int i = 0; i < count; i++) {
            UUID other = persistPerson(UUID.randomUUID(), RoleEnum.BENEVOLE, "06 99 99 99 99");
            QuarkusTransaction.requiringNew().run(() -> registrationRepository.persistConfirmed(
                    eventRepository.findById(eventId), userRepository.findById(other)));
        }
    }

    private ValidatableResponse signUp(UUID eventId) {
        return given().when().put("/{id}/registration", eventId).then();
    }

    private ValidatableResponse withdraw(UUID eventId) {
        return given().when().delete("/{id}/registration", eventId).then();
    }

    // --- Sign-up ---

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void signingUpForAnOpenEventGivesAPlace() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, null);
        signUp(event).statusCode(200)
                .body("eventId", equalTo(event.toString()))
                .body("mode", equalTo("conducteur"))
                .body("status", equalTo("participant"));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void underTheMaximumGivesAPlaceAndAtTheMaximumGoesOnTheListeDAttente() {
        UUID roomLeft = futureEvent(EventStatusEnum.OUVERT, 3);
        fill(roomLeft, 2);
        signUp(roomLeft).statusCode(200).body("status", equalTo("participant"));

        UUID full = futureEvent(EventStatusEnum.OUVERT, 2);
        fill(full, 2);
        signUp(full).statusCode(200).body("status", equalTo("en_attente"));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void aCompletEventGoesStraightOnTheListeDAttente() {
        UUID event = futureEvent(EventStatusEnum.COMPLET, null);
        signUp(event).statusCode(200).body("status", equalTo("en_attente"));
    }

    @ParameterizedTest(name = "stored {0}, from {1}h to {2}h: refused")
    @CsvSource({
            "PLANIFICATION, 24, 48",
            "ANNULE, 24, 48",
            "OUVERT, -1, 1",
            "OUVERT, -48, -24",
    })
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void signUpIsRefusedOutsideOuvertAndComplet(EventStatusEnum stored, long start, long end) {
        UUID event = persistEvent(stored, start, end, null);
        signUp(event).statusCode(400);
        assertEquals(0, QuarkusTransaction.requiringNew().call(() -> registrationRepository.count("event.eventId", event)));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void signingUpTwiceKeepsTheFirstSignUp() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, 1);
        signUp(event).statusCode(200).body("status", equalTo("participant"));
        signUp(event).statusCode(200).body("status", equalTo("participant"));
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> registrationRepository.count("event.eventId", event)));
    }

    @Test
    @TestSecurity(user = "no-phone", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = NO_PHONE))
    void signUpWithoutAPhoneNumberAsksForIt() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, null);
        signUp(event).statusCode(422).body("error", equalTo("phone-required"));
    }

    @Test
    void anonymousVisiteurCannotSignUp() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, null);
        signUp(event).statusCode(401);
    }

    // --- Withdrawal ---

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void aParticipantWithdrawsAndNobodyMovesUp() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, 1);
        signUp(event).statusCode(200).body("status", equalTo("participant"));
        UUID waiting = persistPerson(UUID.randomUUID(), RoleEnum.BENEVOLE, "06 11 11 11 11");
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persistWaiting(
                eventRepository.findById(event), userRepository.findById(waiting)));

        withdraw(event).statusCode(204);

        assertEquals(List.of(true), QuarkusTransaction.requiringNew().call(() ->
                registrationRepository.list("event.eventId", event).stream().map(r -> r.isWaiting()).toList()));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void someoneOnTheListeDAttenteWithdraws() {
        UUID event = futureEvent(EventStatusEnum.COMPLET, null);
        signUp(event).statusCode(200).body("status", equalTo("en_attente"));
        withdraw(event).statusCode(204);
        given().when().get("/registrations").then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void withdrawalWorksUntilArchive() {
        UUID inProgress = futureEvent(EventStatusEnum.OUVERT, null);
        signUp(inProgress).statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            EventEntity e = eventRepository.findById(inProgress);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusHours(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusHours(1));
        });
        withdraw(inProgress).statusCode(204);

        UUID archived = futureEvent(EventStatusEnum.OUVERT, null);
        signUp(archived).statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            EventEntity e = eventRepository.findById(archived);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusHours(48));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).minusHours(24));
        });
        withdraw(archived).statusCode(400);
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void withdrawingWithoutASignUpIsNotFound() {
        withdraw(futureEvent(EventStatusEnum.OUVERT, null)).statusCode(404);
    }

    // --- Mes événements ---

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void myRegistrationsShowParticipantOrEnAttente() {
        UUID open = futureEvent(EventStatusEnum.OUVERT, null);
        UUID full = futureEvent(EventStatusEnum.COMPLET, null);
        signUp(open).statusCode(200);
        signUp(full).statusCode(200);

        given().when().get("/registrations").then().statusCode(200)
                .body("$", hasSize(2))
                .body("find { it.eventId == '" + open + "' }.status", equalTo("participant"))
                .body("find { it.eventId == '" + full + "' }.status", equalTo("en_attente"));
    }

    // --- Maximum ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSetsAndClearsTheMaximum() {
        UUID event = futureEvent(EventStatusEnum.OUVERT, null);
        given().contentType(ContentType.JSON).body(Map.of("eventId", event.toString(), "maxParticipants", 40))
                .when().patch("/").then().statusCode(200).body("maxParticipants", equalTo(40));

        given().contentType(ContentType.JSON).body("{\"eventId\":\"" + event + "\",\"maxParticipants\":0}")
                .when().patch("/").then().statusCode(200).body("maxParticipants", equalTo(null));
    }

    // --- Profile ---

    @Test
    @TestSecurity(user = "no-phone", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = NO_PHONE))
    void aPersonAddsTheirPhoneThenSignsUp() {
        given().basePath("/api/users").contentType(ContentType.JSON)
                .body(Map.of("firstName", "Sophie", "lastName", "Kremer", "phone", "06 23 45 67 89"))
                .when().patch("/me").then().statusCode(200)
                .body("phone", equalTo("06 23 45 67 89"));

        signUp(futureEvent(EventStatusEnum.OUVERT, null)).statusCode(200).body("status", equalTo("participant"));
    }
}
