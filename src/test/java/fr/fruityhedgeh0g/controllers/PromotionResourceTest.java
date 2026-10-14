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
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Promotions from the "Inscrits" screen: PUT /api/users/{id}/role, mirrored to Keycloak. */
@QuarkusTest
@TestHTTPEndpoint(UserController.class)
public class PromotionResourceTest {

    static final String MEMBRE_ID = "00000000-0000-0000-0001-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0001-000000000004";
    static final String BUREAU_ID = "00000000-0000-0000-0001-000000000005";
    static final String ADMIN_ID = "00000000-0000-0000-0001-000000000006";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0001-000000000007";

    static final Map<String, RoleEnum> ACTORS = Map.of(
            MEMBRE_ID, RoleEnum.MEMBRE,
            CHEF_ID, RoleEnum.CHEF_DE_GROUPE,
            BUREAU_ID, RoleEnum.BUREAU,
            ADMIN_ID, RoleEnum.ADMIN,
            SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN
    );

    @Inject
    UserRepository userRepository;

    @Inject
    FakeKeycloakRoleMirror keycloak;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    private final List<UUID> created = new ArrayList<>();
    /** Everyone from Membre up belongs to this Secteur (ADR 0004); the Super admin to none. */
    private UUID sector;
    /** An Event of that Secteur, which every Bénévole here rode, so its Bureau may promote them (ADR 0004). */
    private UUID ride;

    @BeforeEach
    void seedActors() {
        keycloak.reset();
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        ride = QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(10));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(9));
            e.setSector(sectorRepository.findById(sector));
            eventRepository.persist(e);
            return e.getEventId();
        });
        ACTORS.forEach((id, role) -> created.add(persist(UUID.fromString(id), role)));
    }

    @AfterEach
    void removePersons() {
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.eventId", ride);
            eventRepository.deleteById(ride);
            created.forEach(userRepository::deleteById);
            eventRepository.flush();
            sectorRepository.deleteById(sector);
        });
        created.clear();
        keycloak.reset();
    }

    private UUID persist(UUID id, RoleEnum role) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(id).firstName("Test").lastName(role.name()).role(role)
                        .sector(role.isAtLeast(RoleEnum.MEMBRE) && role != RoleEnum.SUPER_ADMIN ? sectorRepository.findById(sector) : null)
                        .build()
        ));
        if (role == RoleEnum.BENEVOLE)
            QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.conducteur(
                    eventRepository.findById(ride), userRepository.findById(id), false)));
        return id;
    }

    private UUID persistPerson(RoleEnum role) {
        UUID id = persist(UUID.randomUUID(), role);
        created.add(id);
        return id;
    }

    private RoleEnum storedRole(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> userRepository.findById(id).getRole());
    }

    /** An Admin is appointed for a Secteur, which the Super admin names. */
    private ValidatableResponse setRole(UUID personId, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", role);
        if (role.equals("admin")) body.put("sectorId", sector.toString());
        return given().contentType(ContentType.JSON).body(body)
                .when().put("/{id}/role", personId).then();
    }

    /** Moves a new person from {@code current} to {@code next}; checks status, stored Role and mirror. */
    private void assertPromotion(String current, String next, boolean allowed) {
        UUID target = persistPerson(RoleEnum.valueOf(current));
        setRole(target, next.toLowerCase()).statusCode(allowed ? 200 : 403);

        RoleEnum expected = RoleEnum.valueOf(allowed ? next : current);
        assertEquals(expected, storedRole(target));
        assertEquals(
                allowed ? List.of(new FakeKeycloakRoleMirror.Call(target, expected)) : List.of(),
                keycloak.calls()
        );
    }

    @ParameterizedTest(name = "bureau sets {0} to {1}: {2}")
    @CsvSource({
            "BENEVOLE, MEMBRE, true",
            "MEMBRE, CHEF_DE_GROUPE, true",
            "CHEF_DE_GROUPE, MEMBRE, true",
            "MEMBRE, BENEVOLE, true",
            "MEMBRE, BUREAU, false",
            "BUREAU, MEMBRE, false",
            "ADMIN, MEMBRE, false",
            "BENEVOLE, VISITEUR, false",
    })
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureau(String current, String next, boolean allowed) {
        assertPromotion(current, next, allowed);
    }

    @ParameterizedTest(name = "admin sets {0} to {1}: {2}")
    @CsvSource({
            "MEMBRE, BUREAU, true",
            "BUREAU, MEMBRE, true",
            "BENEVOLE, MEMBRE, true",
            "BUREAU, ADMIN, false",
            "ADMIN, BUREAU, false",
    })
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void admin(String current, String next, boolean allowed) {
        assertPromotion(current, next, allowed);
    }

    @ParameterizedTest(name = "super admin sets {0} to {1}: {2}")
    @CsvSource({
            "BUREAU, ADMIN, true",
            "ADMIN, BUREAU, true",
            "ADMIN, BENEVOLE, true",
            "ADMIN, SUPER_ADMIN, false",
            "SUPER_ADMIN, ADMIN, false",
    })
    @TestSecurity(user = "super_admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void superAdmin(String current, String next, boolean allowed) {
        assertPromotion(current, next, allowed);
    }

    @ParameterizedTest(name = "chef de groupe sets {0} to {1}: {2}")
    @CsvSource({"BENEVOLE, MEMBRE, false"})
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void chefDeGroupe(String current, String next, boolean allowed) {
        assertPromotion(current, next, allowed);
    }

    @ParameterizedTest(name = "membre sets {0} to {1}: {2}")
    @CsvSource({"BENEVOLE, MEMBRE, false"})
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void membre(String current, String next, boolean allowed) {
        assertPromotion(current, next, allowed);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void nobodyChangesTheirOwnRole() {
        setRole(UUID.fromString(ADMIN_ID), "membre").statusCode(403);
        assertEquals(RoleEnum.ADMIN, storedRole(UUID.fromString(ADMIN_ID)));
        assertEquals(List.of(), keycloak.calls());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void missingRoleIsABadRequest() {
        UUID target = persistPerson(RoleEnum.BENEVOLE);
        given().contentType(ContentType.JSON).body(Map.of()).when().put("/{id}/role", target).then().statusCode(400);
        assertEquals(RoleEnum.BENEVOLE, storedRole(target));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void unknownPersonIsNotFound() {
        setRole(UUID.randomUUID(), "membre").statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void mirrorFailureKeepsTheDatabaseRole() {
        keycloak.failing(true);
        UUID target = persistPerson(RoleEnum.BENEVOLE);

        setRole(target, "membre").statusCode(200).body("role", equalTo("membre"));

        assertEquals(RoleEnum.MEMBRE, storedRole(target));
        assertEquals(List.of(new FakeKeycloakRoleMirror.Call(target, RoleEnum.MEMBRE)), keycloak.calls());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureauListsPersonsWithTheirRole() {
        UUID target = persistPerson(RoleEnum.CHEF_DE_GROUPE);
        given().when().get("/").then().statusCode(200)
                .body("find { it.userId == '" + target + "' }.role", equalTo("chef_de_groupe"));
    }
}
