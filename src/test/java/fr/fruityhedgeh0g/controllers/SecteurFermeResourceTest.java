package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only the Super admin opens, renames, closes and reopens a Secteur; the Bureau keeps its description.
 * A Secteur fermé is read-only and seen, with its Groupes and Events, by the Super admin only (ADR 0003).
 */
@QuarkusTest
@TestHTTPEndpoint(SectorController.class)
public class SecteurFermeResourceTest {

    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0012-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0012-000000000002";
    static final String BUREAU_ID = "00000000-0000-0000-0012-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0012-000000000004";
    static final String CONDUCTEUR_ID = "00000000-0000-0000-0012-000000000005";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    GroupRepository groupRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    private UUID sector;
    private UUID nord;
    private UUID upcoming;
    private UUID running;
    private UUID past;

    @BeforeEach
    void seed() {
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN);
        persistPerson(ADMIN_ID, RoleEnum.ADMIN);
        persistPerson(BUREAU_ID, RoleEnum.BUREAU);
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE);
        persistPerson(CONDUCTEUR_ID, RoleEnum.BENEVOLE);
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).description("Avant").build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            // From Membre up, everyone belongs to this Secteur (ADR 0004)
            List.of(ADMIN_ID, BUREAU_ID, CHEF_ID).forEach(id -> userRepository.findById(UUID.fromString(id)).setSector(s));
            GroupEntity group = GroupEntity.builder().name("Test Nord " + UUID.randomUUID()).sector(s)
                    .chef(userRepository.findById(UUID.fromString(CHEF_ID))).build();
            groupRepository.persist(group);
            nord = group.getGroupId();

            LocalDateTime now = LocalDateTime.now(EventEntity.ZONE);
            upcoming = persistEvent(s, EventStatusEnum.OUVERT, now.plusDays(1), now.plusDays(2));
            running = persistEvent(s, EventStatusEnum.OUVERT, now.minusHours(1), now.plusHours(1));
            past = persistEvent(s, EventStatusEnum.OUVERT, now.minusDays(2), now.minusDays(1));

            EventRegistrationEntity signUp = EventRegistrationEntity.conducteur(
                    eventRepository.findById(upcoming), userRepository.findById(UUID.fromString(CONDUCTEUR_ID)), false);
            signUp.placeIn(group);
            registrationRepository.persist(signUp);
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            groupRepository.delete("sector.sectorId", sector);
            List.of(SUPER_ADMIN_ID, ADMIN_ID, BUREAU_ID, CHEF_ID, CONDUCTEUR_ID).forEach(id -> userRepository.deleteById(UUID.fromString(id)));
            // The bulk delete below does not flush the Event and person deletions first
            eventRepository.flush();
            sectorRepository.delete("sectorId = ?1 or name like ?2", sector, "Test Nouveau %");
        });
    }

    private void persistPerson(String id, RoleEnum role) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(UUID.fromString(id)).firstName("Test").lastName(role.name()).role(role).phone("06 00 00 00 00").build()));
    }

    private UUID persistEvent(SectorEntity s, EventStatusEnum status, LocalDateTime start, LocalDateTime end) {
        EventEntity e = new EventEntity();
        e.setName("Test Balade " + status + " " + start);
        e.setStatus(status);
        e.setStartDateTime(start);
        e.setEndDateTime(end);
        e.setSector(s);
        eventRepository.persist(e);
        return e.getEventId();
    }

    private void closedInDatabase() {
        QuarkusTransaction.requiringNew().run(() -> sectorRepository.findById(sector).setClosed(true));
    }

    private ValidatableResponse close() {
        return given().when().post("/{id}/close", sector).then();
    }

    private ValidatableResponse reopen() {
        return given().when().post("/{id}/reopen", sector).then();
    }

    private ValidatableResponse patch(String name, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("sectorId", sector.toString());
        body.put("name", name);
        body.put("description", description);
        return given().contentType(ContentType.JSON).body(body).when().patch("/").then();
    }

    private String currentName() {
        return QuarkusTransaction.requiringNew().call(() -> sectorRepository.findById(sector).getName());
    }

    // --- Who manages a Secteur ---

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminOpensAndRenamesASecteur() {
        given().contentType(ContentType.JSON).body(Map.of("name", "Test Nouveau " + UUID.randomUUID(), "description", ""))
                .when().post("/").then().statusCode(200);
        patch("Test Renommé " + UUID.randomUUID(), "Avant").statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void belowTheSuperAdminNoSecteurIsOpenedRenamedOrClosed() {
        given().contentType(ContentType.JSON).body(Map.of("name", "Test Refusé", "description", ""))
                .when().post("/").then().statusCode(403);
        String name = currentName();
        patch("Test Renommé", "Avant").statusCode(403);
        close().statusCode(403);
        reopen().statusCode(403);
        assertEquals(name, currentName());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauKeepsTheDescriptionUpToDate() {
        patch(currentName(), "Après").statusCode(200).body("description", equalTo("Après"));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void belowTheBureauTheDescriptionIsNotEdited() {
        patch(currentName(), "Après").statusCode(403);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void aSecteurIsNeverDeleted() {
        given().when().delete("/{id}", sector).then().statusCode(405);
    }

    // --- Closing ---

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void closingKeepsTheLineage() {
        close().statusCode(200).body("closed", equalTo(true));

        given().basePath("/api/events").when().get("/{id}", upcoming).then().statusCode(200).body("status", equalTo("annule"));
        given().basePath("/api/events").when().get("/{id}", running).then().statusCode(200).body("status", equalTo("archive"));
        given().basePath("/api/events").when().get("/{id}", past).then().statusCode(200).body("status", equalTo("archive"));
        given().basePath("/api/events").when().get("/{id}/roster", upcoming).then().statusCode(200)
                .body("participants[0].personId", equalTo(CONDUCTEUR_ID))
                .body("participants[0].group.groupId", equalTo(nord.toString()));
        given().basePath("/api/groups").when().get().then().statusCode(200)
                .body("find { it.groupId == '" + nord + "' }.chef", nullValue());
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void aSecteurFermeIsReadOnly() {
        closedInDatabase();
        patch(currentName(), "Après").statusCode(400);
        given().basePath("/api/groups").when().put("/{g}/chef/{u}", nord, CHEF_ID).then().statusCode(400);
        given().basePath("/api/events").contentType(ContentType.JSON)
                .body(Map.of("name", "Test Refusée", "sectorId", sector.toString(),
                        "startDateTime", "2030-01-01T10:00:00", "endDateTime", "2030-01-01T18:00:00"))
                .when().post().then().statusCode(400);
        given().basePath("/api/events").when().delete("/{e}/roster/{p}", upcoming, CONDUCTEUR_ID).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void onlyTheSuperAdminSeesASecteurFermeItsGroupesAndItsEvents() {
        closedInDatabase();
        given().when().get("/").then().statusCode(200).body("sectorId", not(hasItem(sector.toString())));
        given().when().get("/{id}", sector).then().statusCode(404);
        given().basePath("/api/groups").when().get().then().statusCode(200).body("groupId", not(hasItem(nord.toString())));
        given().basePath("/api/events").when().get().then().statusCode(200).body("eventId", not(hasItem(past.toString())));
        given().basePath("/api/events").when().get("/{id}", past).then().statusCode(404);
        given().basePath("/api/events").when().get("/{id}/roster", upcoming).then().statusCode(404);
    }

    @Test
    void anonymousVisiteursDoNotSeeASecteurFermesGroupesOrEvents() {
        closedInDatabase();
        given().basePath("/api/groups").when().get().then().statusCode(200).body("groupId", not(hasItem(nord.toString())));
        given().basePath("/api/events").when().get().then().statusCode(200).body("eventId", not(hasItem(past.toString())));
        given().basePath("/api/sectors").when().get().then().statusCode(200).body("sectorId", not(hasItem(sector.toString())));
        given().basePath("/api/sectors").when().get("/" + sector).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void aSecteurFermesEventsLeaveMesEvenements() {
        closedInDatabase();
        given().basePath("/api/events").when().get("/registrations").then().statusCode(200)
                .body("eventId", not(hasItem(upcoming.toString())));
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminSeesAndReopensASecteurFerme() {
        close().statusCode(200);
        given().when().get("/").then().statusCode(200)
                .body("find { it.sectorId == '" + sector + "' }.closed", equalTo(true));

        reopen().statusCode(200).body("closed", equalTo(false));
        given().basePath("/api/events").when().get("/{id}", upcoming).then().statusCode(200).body("status", equalTo("annule"));
        given().basePath("/api/groups").when().get().then().statusCode(200)
                .body("find { it.groupId == '" + nord + "' }.chef", nullValue());
        patch(currentName(), "Après").statusCode(200);
    }
}
