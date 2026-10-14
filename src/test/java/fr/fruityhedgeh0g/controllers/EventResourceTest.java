package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Event lifecycle: Secteur, manual and date-driven statuses, visibility of Planification. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class EventResourceTest {

    static final String MEMBRE_ID = "00000000-0000-0000-0004-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0004-000000000004";
    static final String BUREAU_ID = "00000000-0000-0000-0004-000000000005";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    EventRepository eventRepository;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (var actor : Map.of(MEMBRE_ID, RoleEnum.MEMBRE, CHEF_ID, RoleEnum.CHEF_DE_GROUPE, BUREAU_ID, RoleEnum.BUREAU).entrySet()) {
                UUID id = UUID.fromString(actor.getKey());
                userRepository.persist(UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(id).firstName("Test").lastName("Test").role(actor.getValue()).build());
                persons.add(id);
            }
            SectorEntity algrange = SectorEntity.builder().name("Test Algrange " + UUID.randomUUID()).build();
            sectorRepository.persist(algrange);
            sector = algrange.getSectorId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            sectorRepository.deleteById(sector);
            persons.forEach(userRepository::deleteById);
        });
        persons.clear();
    }

    /** Stores an Event directly, with dates relative to now (in hours). */
    private UUID persistEvent(EventStatusEnum stored, long startInHours, long endInHours) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity event = new EventEntity();
            event.setName("Test " + stored);
            event.setStatus(stored);
            event.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusHours(startInHours));
            event.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusHours(endInHours));
            event.setSector(sectorRepository.findById(sector));
            eventRepository.persist(event);
            return event.getEventId();
        });
    }

    private UUID futureEvent(EventStatusEnum stored) {
        return persistEvent(stored, 24, 48);
    }

    private EventStatusEnum storedStatus(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> eventRepository.findById(id).getStatus());
    }

    private ValidatableResponse moveTo(UUID id, String status) {
        return given().contentType(ContentType.JSON).body(Map.of("status", status))
                .when().put("/{id}/status", id).then();
    }

    private Map<String, Object> newEvent() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Balade");
        body.put("description", "Balade solidaire");
        body.put("startDateTime", LocalDateTime.now().plusDays(10).withNano(0).toString());
        body.put("endDateTime", LocalDateTime.now().plusDays(10).plusHours(6).withNano(0).toString());
        body.put("sectorId", sector.toString());
        return body;
    }

    // --- Creation ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void creationStartsInPlanificationInItsSecteur() {
        Map<String, Object> body = newEvent();
        body.put("status", "ouvert");
        String id = given().contentType(ContentType.JSON).body(body)
                .when().post("/").then().statusCode(200)
                .body("status", equalTo("planification"))
                .extract().path("eventId");

        given().when().get("/{id}", id).then().statusCode(200)
                .body("sectorId", equalTo(sector.toString()))
                .body("sector.sectorId", equalTo(sector.toString()));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void creationRequiresASecteur() {
        Map<String, Object> body = newEvent();
        body.remove("sectorId");
        given().contentType(ContentType.JSON).body(body).when().post("/").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void creationRequiresAnEndAfterTheStart() {
        Map<String, Object> noEnd = newEvent();
        noEnd.remove("endDateTime");
        given().contentType(ContentType.JSON).body(noEnd).when().post("/").then().statusCode(400);

        Map<String, Object> backwards = newEvent();
        backwards.put("endDateTime", LocalDateTime.now().plusDays(1).withNano(0).toString());
        given().contentType(ContentType.JSON).body(backwards).when().post("/").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void archivedAndCancelledEventsAreNoLongerEdited() {
        UUID archived = persistEvent(EventStatusEnum.OUVERT, -48, -24);
        UUID cancelled = futureEvent(EventStatusEnum.ANNULE);
        String later = LocalDateTime.now().plusDays(30).withNano(0).toString();
        for (UUID id : List.of(archived, cancelled)) {
            given().contentType(ContentType.JSON).body(Map.of("eventId", id.toString(), "endDateTime", later))
                    .when().patch("/").then().statusCode(400);
        }
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureauEditsAnEvent() {
        UUID id = futureEvent(EventStatusEnum.OUVERT);
        given().contentType(ContentType.JSON).body(Map.of("eventId", id.toString(), "name", "Test Renommé", "status", "annule"))
                .when().patch("/").then().statusCode(200)
                .body("name", equalTo("Test Renommé"))
                .body("status", equalTo("ouvert"));
    }

    // --- Manual transitions ---

    @ParameterizedTest(name = "{0} to {1}: {2}")
    @CsvSource({
            "PLANIFICATION, ouvert, 200",
            "OUVERT, complet, 200",
            "COMPLET, ouvert, 200",
            "PLANIFICATION, annule, 200",
            "OUVERT, annule, 200",
            "COMPLET, annule, 200",
            "PLANIFICATION, complet, 400",
            "OUVERT, planification, 400",
            "OUVERT, en_cours, 400",
            "OUVERT, archive, 400",
            "ANNULE, ouvert, 400",
    })
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void manualTransitions(EventStatusEnum stored, String target, int status) {
        UUID id = futureEvent(stored);
        moveTo(id, target).statusCode(status);
        assertEquals(status == 200 ? EventStatusEnum.valueOf(target.toUpperCase()) : stored, storedStatus(id));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void anEventInProgressCanBeCancelledButNotReopened() {
        UUID id = persistEvent(EventStatusEnum.OUVERT, -1, 1);
        moveTo(id, "complet").statusCode(400);
        moveTo(id, "annule").statusCode(200).body("status", equalTo("annule"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void anArchivedEventCannotBeCancelled() {
        UUID id = persistEvent(EventStatusEnum.OUVERT, -48, -24);
        moveTo(id, "annule").statusCode(400);
        assertEquals(EventStatusEnum.OUVERT, storedStatus(id));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void thereIsNoDeleteEndpoint() {
        UUID id = futureEvent(EventStatusEnum.OUVERT);
        given().when().delete("/{id}", id).then().statusCode(405);
    }

    // --- Date-driven statuses ---

    @Test
    void anOpenEventReadsEnCoursThenArchive() {
        UUID inProgress = persistEvent(EventStatusEnum.OUVERT, -1, 1);
        UUID archived = persistEvent(EventStatusEnum.COMPLET, -48, -24);

        given().when().get("/{id}", inProgress).then().statusCode(200).body("status", equalTo("en_cours"));
        given().when().get("/{id}", archived).then().statusCode(200).body("status", equalTo("archive"));
    }

    // --- Who may manage ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void belowTheBureauNobodyManagesEvents() {
        UUID id = futureEvent(EventStatusEnum.OUVERT);
        given().contentType(ContentType.JSON).body(newEvent()).when().post("/").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("eventId", id.toString(), "name", "Test Refusé"))
                .when().patch("/").then().statusCode(403);
        moveTo(id, "complet").statusCode(403);
    }

    @Test
    void anonymousVisiteurCannotManageEvents() {
        UUID id = futureEvent(EventStatusEnum.OUVERT);
        given().contentType(ContentType.JSON).body(newEvent()).when().post("/").then().statusCode(401);
        moveTo(id, "complet").statusCode(401);
    }

    // --- Visibility of Planification ---

    @Test
    void anonymousVisiteurDoesNotSeePlanification() {
        UUID hidden = futureEvent(EventStatusEnum.PLANIFICATION);
        UUID open = futureEvent(EventStatusEnum.OUVERT);

        given().when().get("/").then().statusCode(200)
                .body("eventId", hasItem(open.toString()))
                .body("eventId", not(hasItem(hidden.toString())));
        given().when().get("/{id}", hidden).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void belowTheBureauPlanificationIsHidden() {
        UUID hidden = futureEvent(EventStatusEnum.PLANIFICATION);
        given().when().get("/").then().statusCode(200).body("eventId", not(hasItem(hidden.toString())));
        given().when().get("/{id}", hidden).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSeesPlanification() {
        UUID hidden = futureEvent(EventStatusEnum.PLANIFICATION);
        given().when().get("/").then().statusCode(200).body("eventId", hasItem(hidden.toString()));
        given().when().get("/{id}", hidden).then().statusCode(200).body("status", equalTo("planification"));
    }
}
