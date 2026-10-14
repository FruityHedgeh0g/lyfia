package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Bureau's roster for an Event: Participants, Liste d'attente, moving up and removing. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class RosterResourceTest {

    static final String CHEF_ID = "00000000-0000-0000-0006-000000000004";
    static final String BUREAU_ID = "00000000-0000-0000-0006-000000000005";

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
        persistPerson(UUID.fromString(CHEF_ID), RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU, "Bureau");
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

    private UUID persistPerson(UUID id, RoleEnum role, String lastName) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(id).firstName("Test").lastName(lastName).role(role).phone("06 00 00 00 00").build()
        ));
        persons.add(id);
        return id;
    }

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

    /** Signs a new person up, `minutesAgo` minutes ago, confirmed or on the Liste d'attente. */
    private UUID signedUp(UUID eventId, String lastName, boolean waiting, int minutesAgo) {
        UUID person = persistPerson(UUID.randomUUID(), RoleEnum.BENEVOLE, lastName);
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity registration = EventRegistrationEntity.conducteur(
                    eventRepository.findById(eventId), userRepository.findById(person), waiting);
            registration.setSignedUpAt(LocalDateTime.now(EventEntity.ZONE).minusMinutes(minutesAgo));
            registration.getJournees().forEach(j -> j.setSignedUpAt(registration.getSignedUpAt()));
            registrationRepository.persist(registration);
        });
        return person;
    }

    private boolean isWaiting(UUID eventId, UUID personId) {
        return QuarkusTransaction.requiringNew().call(() ->
                registrationRepository.findByEventAndPerson(eventId, personId).orElseThrow().status() == RegistrationStatusEnum.EN_ATTENTE);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSeesParticipantsAndTheListeDAttenteInSignUpOrder() {
        UUID event = persistEvent(EventStatusEnum.COMPLET, 24, 48, 2);
        signedUp(event, "Second", false, 50);
        signedUp(event, "First", false, 60);
        signedUp(event, "Later", true, 10);
        signedUp(event, "Earlier", true, 20);

        given().when().get("/{id}/roster", event).then().statusCode(200)
                .body("maxParticipants", equalTo(2))
                .body("participants.lastName", contains("First", "Second"))
                .body("waiting.lastName", contains("Earlier", "Later"))
                .body("participants[0].phone", equalTo("06 00 00 00 00"))
                .body("participants[0].poste", equalTo("conducteur"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void movingUpSucceedsUnderTheMaximum() {
        UUID event = persistEvent(EventStatusEnum.COMPLET, 24, 48, 2);
        signedUp(event, "Participant", false, 60);
        UUID waiting = signedUp(event, "Attente", true, 30);

        given().when().post("/{id}/roster/{person}/promote", event, waiting).then().statusCode(200)
                .body("participants.lastName", contains("Participant", "Attente"))
                .body("waiting", empty());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void movingUpIsRefusedAtTheMaximum() {
        UUID event = persistEvent(EventStatusEnum.OUVERT, 24, 48, 1);
        signedUp(event, "Participant", false, 60);
        UUID waiting = signedUp(event, "Attente", true, 30);

        given().when().post("/{id}/roster/{person}/promote", event, waiting).then().statusCode(400);
        assertEquals(true, isWaiting(event, waiting));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void movingUpWithoutAMaximumAlwaysSucceeds() {
        UUID event = persistEvent(EventStatusEnum.COMPLET, 24, 48, null);
        signedUp(event, "Participant", false, 60);
        UUID waiting = signedUp(event, "Attente", true, 30);

        given().when().post("/{id}/roster/{person}/promote", event, waiting).then().statusCode(200);
        assertEquals(false, isWaiting(event, waiting));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void onlySomeoneOnTheListeDAttenteMovesUp() {
        UUID event = persistEvent(EventStatusEnum.OUVERT, 24, 48, null);
        UUID participant = signedUp(event, "Participant", false, 60);
        given().when().post("/{id}/roster/{person}/promote", event, participant).then().statusCode(400);
        given().when().post("/{id}/roster/{person}/promote", event, UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void removingAParticipantFreesThePlaceWithoutMovingAnyoneUp() {
        UUID event = persistEvent(EventStatusEnum.COMPLET, 24, 48, 1);
        UUID participant = signedUp(event, "Participant", false, 60);
        signedUp(event, "Attente", true, 30);

        given().when().delete("/{id}/roster/{person}", event, participant).then().statusCode(200)
                .body("participants", empty())
                .body("waiting.lastName", contains("Attente"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void anArchivedEventKeepsItsRoster() {
        UUID event = persistEvent(EventStatusEnum.OUVERT, -48, -24, null);
        UUID participant = signedUp(event, "Participant", false, 60);
        UUID waiting = signedUp(event, "Attente", true, 30);

        given().when().delete("/{id}/roster/{person}", event, participant).then().statusCode(400);
        given().when().post("/{id}/roster/{person}/promote", event, waiting).then().statusCode(400);
        given().when().get("/{id}/roster", event).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void belowTheBureauTheRosterIsRefused() {
        UUID event = persistEvent(EventStatusEnum.OUVERT, 24, 48, null);
        UUID participant = signedUp(event, "Participant", false, 60);
        UUID waiting = signedUp(event, "Attente", true, 30);

        given().when().get("/{id}/roster", event).then().statusCode(403);
        given().when().post("/{id}/roster/{person}/promote", event, waiting).then().statusCode(403);
        given().when().delete("/{id}/roster/{person}", event, participant).then().statusCode(403);
    }

    @Test
    void anonymousVisiteurCannotSeeTheRoster() {
        UUID event = persistEvent(EventStatusEnum.OUVERT, 24, 48, null);
        given().when().get("/{id}/roster", event).then().statusCode(401);
    }
}
