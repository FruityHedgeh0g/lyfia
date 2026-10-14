package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakRoleMirror;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Inscrits list and promotions of the Bureau and Admin: their Secteur's people, and the Bénévoles
 * of the pool who rode with it; one Président per Secteur (ADR 0004).
 */
@QuarkusTest
@TestHTTPEndpoint(UserController.class)
public class InscritsScopeResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0016-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0016-000000000002";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0016-000000000003";
    static final String MEMBRE_ID = "00000000-0000-0000-0016-000000000004";
    static final String OTHER_MEMBRE_ID = "00000000-0000-0000-0016-000000000005";
    static final String RODE_WITH_US_ID = "00000000-0000-0000-0016-000000000006";
    static final String RODE_ELSEWHERE_ID = "00000000-0000-0000-0016-000000000007";
    static final String NEVER_RODE_ID = "00000000-0000-0000-0016-000000000008";
    static final String OTHER_BUREAU_ID = "00000000-0000-0000-0016-000000000009";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    @Inject
    FakeKeycloakRoleMirror keycloak;

    private final List<UUID> persons = new ArrayList<>();
    private UUID algrange;
    private UUID thionville;

    @BeforeEach
    void seed() {
        keycloak.reset();
        algrange = persistSector("Test Algrange ");
        thionville = persistSector("Test Thionville ");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, algrange);
        persistPerson(ADMIN_ID, RoleEnum.ADMIN, algrange);
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN, null);
        persistPerson(MEMBRE_ID, RoleEnum.MEMBRE, algrange);
        persistPerson(OTHER_MEMBRE_ID, RoleEnum.MEMBRE, thionville);
        persistPerson(OTHER_BUREAU_ID, RoleEnum.BUREAU, thionville);
        persistPerson(RODE_WITH_US_ID, RoleEnum.BENEVOLE, null);
        persistPerson(RODE_ELSEWHERE_ID, RoleEnum.BENEVOLE, null);
        persistPerson(NEVER_RODE_ID, RoleEnum.BENEVOLE, null);
        signedUp(RODE_WITH_US_ID, persistEvent(algrange));
        signedUp(RODE_ELSEWHERE_ID, persistEvent(thionville));
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            List<UUID> sectors = List.of(algrange, thionville);
            registrationRepository.delete("event.sector.sectorId in ?1", sectors);
            eventRepository.list("sector.sectorId in ?1", sectors).forEach(eventRepository::delete);
            persons.forEach(userRepository::deleteById);
            eventRepository.flush();
            sectorRepository.delete("sectorId in ?1", sectors);
        });
        persons.clear();
        keycloak.reset();
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
                UserEntity.builder().userId(userId).firstName("Test").lastName(role.name()).role(role)
                        .sector(sectorId == null ? null : sectorRepository.findById(sectorId)).build()));
        persons.add(userId);
    }

    private UUID persistEvent(UUID sectorId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            // A past Event still counts: riding with a Secteur once is enough
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(10));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(9));
            e.setSector(sectorRepository.findById(sectorId));
            eventRepository.persist(e);
            return e.getEventId();
        });
    }

    private void signedUp(String personId, UUID eventId) {
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.conducteur(
                eventRepository.findById(eventId), userRepository.findById(UUID.fromString(personId)), false)));
    }

    private void promoteToMembre(String personId, int expectedStatus) {
        given().contentType(ContentType.JSON).body(Map.of("role", "membre"))
                .when().put("/{id}/role", personId).then().statusCode(expectedStatus);
    }

    private void makePresident(String personId, int expectedStatus) {
        given().contentType(ContentType.JSON).body(Map.of("function", "president"))
                .when().put("/{id}/bureau-function", personId).then().statusCode(expectedStatus);
    }

    private boolean isPresident(String personId) {
        return QuarkusTransaction.requiringNew().call(() -> userRepository.findById(UUID.fromString(personId)).isPresident());
    }

    // --- The Inscrits list ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSeesItsSecteursPeopleAndTheBenevolesWhoRodeWithIt() {
        given().when().get().then().statusCode(200)
                .body("userId", hasItems(BUREAU_ID, ADMIN_ID, MEMBRE_ID, RODE_WITH_US_ID))
                .body("userId", not(hasItem(OTHER_MEMBRE_ID)))
                .body("userId", not(hasItem(RODE_ELSEWHERE_ID)))
                .body("userId", not(hasItem(NEVER_RODE_ID)))
                .body("userId", not(hasItem(SUPER_ADMIN_ID)));
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminSeesEveryone() {
        given().when().get().then().statusCode(200)
                .body("userId", hasItems(BUREAU_ID, OTHER_MEMBRE_ID, RODE_ELSEWHERE_ID, NEVER_RODE_ID));
    }

    // --- Promotions ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauPromotesOnlyBenevolesWhoRodeWithItsSecteur() {
        promoteToMembre(NEVER_RODE_ID, 403);
        promoteToMembre(RODE_ELSEWHERE_ID, 403);
        promoteToMembre(RODE_WITH_US_ID, 200);
    }

    // --- Président ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminMakesThePresidentOfTheirOwnSecteurOnly() {
        makePresident(OTHER_BUREAU_ID, 403);
        makePresident(BUREAU_ID, 200);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void eachSecteurHasItsOwnPresident() {
        makePresident(BUREAU_ID, 200);
        makePresident(OTHER_BUREAU_ID, 200);
        assertTrue(isPresident(BUREAU_ID));
        assertTrue(isPresident(OTHER_BUREAU_ID));

        // Another Président of the same Secteur replaces only that Secteur's Président
        persistPerson("00000000-0000-0000-0016-000000000010", RoleEnum.BUREAU, algrange);
        makePresident("00000000-0000-0000-0016-000000000010", 200);
        assertFalse(isPresident(BUREAU_ID));
        assertTrue(isPresident(OTHER_BUREAU_ID));
    }
}
