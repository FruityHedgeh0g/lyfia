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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * The Bureau and Admin manage only their own Secteur's Events; Membres ride only at their own Secteur's
 * Events, Bénévoles at any (ADR 0004); the Super admin account rides at none, its holder rides through their own (ADR 0006).
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class EventScopeResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0015-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0015-000000000002";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0015-000000000003";
    static final String MEMBRE_ID = "00000000-0000-0000-0015-000000000004";
    static final String BENEVOLE_ID = "00000000-0000-0000-0015-000000000005";
    static final String PILOTE_ID = "00000000-0000-0000-0015-000000000006";

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

    private final List<UUID> persons = new ArrayList<>();
    private UUID algrange;
    private UUID thionville;
    private UUID ourEvent;
    private UUID theirEvent;
    private UUID theirGroup;

    @BeforeEach
    void seed() {
        algrange = persistSector("Test Algrange ");
        thionville = persistSector("Test Thionville ");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, algrange);
        persistPerson(ADMIN_ID, RoleEnum.ADMIN, algrange);
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN, null);
        persistPerson(MEMBRE_ID, RoleEnum.MEMBRE, algrange);
        persistPerson(BENEVOLE_ID, RoleEnum.BENEVOLE, null);
        persistPerson(PILOTE_ID, RoleEnum.BENEVOLE, null);
        ourEvent = persistEvent(algrange);
        theirEvent = persistEvent(thionville);
        QuarkusTransaction.requiringNew().run(() -> {
            GroupEntity g = GroupEntity.builder().name("Test Sud " + UUID.randomUUID()).sector(sectorRepository.findById(thionville)).build();
            groupRepository.persist(g);
            theirGroup = g.getGroupId();
            EventRegistrationEntity r = EventRegistrationEntity.pilote(
                    eventRepository.findById(theirEvent), userRepository.findById(UUID.fromString(PILOTE_ID)), false);
            r.requestGroup(g);
            registrationRepository.persist(r);
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            List<UUID> sectors = List.of(algrange, thionville);
            registrationRepository.delete("event.sector.sectorId in ?1 and pilote is not null", sectors);
            registrationRepository.delete("event.sector.sectorId in ?1", sectors);
            eventRepository.list("sector.sectorId in ?1", sectors).forEach(eventRepository::delete);
            groupRepository.delete("sector.sectorId in ?1", sectors);
            persons.forEach(userRepository::deleteById);
            eventRepository.flush();
            sectorRepository.delete("sectorId in ?1", sectors);
        });
        persons.clear();
    }

    private UUID persistSector(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name(name + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
    }

    private void persistPerson(String id, RoleEnum role, UUID sectorId) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(userId).firstName("Test").lastName(role.name()).role(role).phone("06 00 00 00 00")
                        .sector(sectorId == null ? null : sectorRepository.findById(sectorId)).build()));
        persons.add(userId);
    }

    private UUID persistEvent(UUID sectorId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
            e.setSector(sectorRepository.findById(sectorId));
            eventRepository.persist(e);
            return e.getEventId();
        });
    }

    private ValidatableResponse create(UUID sectorId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("name", "Test Nouvelle", "sectorId", sectorId.toString(),
                        "startDateTime", "2031-01-01T10:00:00", "endDateTime", "2031-01-01T18:00:00"))
                .when().post().then();
    }

    private ValidatableResponse signUp(UUID eventId) {
        return given().when().put("/{id}/registration", eventId).then();
    }

    // --- Managing Events ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauCreatesEventsInItsOwnSecteurOnly() {
        create(algrange).statusCode(200);
        create(thionville).statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauManagesOnlyItsOwnSecteursEvents() {
        given().when().get("/{id}/roster", ourEvent).then().statusCode(200);
        given().when().get("/{id}/roster", theirEvent).then().statusCode(403);
        given().when().get("/{id}/roster/export", theirEvent).then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("status", "complet"))
                .when().put("/{id}/status", theirEvent).then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("maximum", 3))
                .when().put("/{e}/groups/{g}/maximum", theirEvent, theirGroup).then().statusCode(403);
        given().when().post("/{e}/demandes/{p}/accept", theirEvent, PILOTE_ID).then().statusCode(403);
        given().when().delete("/{e}/roster/{p}", theirEvent, PILOTE_ID).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminEditsOnlyTheirSecteursEvents() {
        given().contentType(ContentType.JSON).body(Map.of("eventId", theirEvent.toString(), "name", "Test Renommée"))
                .when().patch().then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("eventId", ourEvent.toString(), "name", "Test Renommée"))
                .when().patch().then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminManagesEverySecteursEvents() {
        given().when().get("/{id}/roster", theirEvent).then().statusCode(200);
        create(thionville).statusCode(200);
    }

    // --- Riding ---

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void aMembreRidesOnlyAtTheirOwnSecteursEvents() {
        signUp(theirEvent).statusCode(400);
        given().queryParam("piloteId", PILOTE_ID).when().put("/{id}/registration", theirEvent).then().statusCode(400);
        signUp(ourEvent).statusCode(200);
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE_ID))
    void aBenevoleRidesAtAnySecteursEvents() {
        signUp(theirEvent).statusCode(200);
        signUp(ourEvent).statusCode(200);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminAccountRidesAtNoEvent() {
        signUp(theirEvent).statusCode(400);
        signUp(ourEvent).statusCode(400);
    }
}
