package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Fonction au Bureau: PUT /api/users/{id}/bureau-function (ADR 0001). The Bureau Role always sits on the Bureau, an
 * Admin by choice; one Président per Secteur; the Fonction follows the Role between Bureau and Admin.
 */
@QuarkusTest
@TestHTTPEndpoint(UserController.class)
public class BureauFunctionResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0002-000000000005";
    static final String ADMIN_ID = "00000000-0000-0000-0002-000000000006";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0002-000000000007";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    private final List<UUID> created = new ArrayList<>();
    /** Everyone from Membre up belongs to this Secteur (ADR 0004); the Super admin to none. */
    private UUID sector;

    @BeforeEach
    void seedActors() {
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        persist(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU);
        persist(UUID.fromString(ADMIN_ID), RoleEnum.ADMIN);
        persist(UUID.fromString(SUPER_ADMIN_ID), RoleEnum.SUPER_ADMIN);
    }

    @AfterEach
    void removePersons() {
        QuarkusTransaction.requiringNew().run(() -> {
            created.forEach(userRepository::deleteById);
            sectorRepository.deleteById(sector);
        });
        created.clear();
    }

    private UUID persist(UUID id, RoleEnum role) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(id).firstName("Test").lastName(role.name()).role(role)
                        .sector(role.isAtLeast(RoleEnum.MEMBRE) && role != RoleEnum.SUPER_ADMIN ? sectorRepository.findById(sector) : null)
                        .build()
        ));
        created.add(id);
        return id;
    }

    private UUID persistPerson(RoleEnum role) {
        return persist(UUID.randomUUID(), role);
    }

    private BureauFunctionEnum functionOf(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> userRepository.findById(id).getBureauFunction());
    }

    private ValidatableResponse setFunction(UUID personId, String function) {
        Map<String, Object> body = new HashMap<>();
        body.put("function", function);
        return given().contentType(ContentType.JSON).body(body)
                .when().put("/{id}/bureau-function", personId).then();
    }

    private ValidatableResponse changeRole(UUID personId, Map<String, String> change) {
        return given().contentType(ContentType.JSON).body(change).when().put("/{id}/role", personId).then();
    }

    // --- The Bureau Role ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void theBureauRoleSitsOnTheBureau() {
        UUID bureau = persistPerson(RoleEnum.BUREAU);
        given().when().get("/").then().statusCode(200)
                .body("find { it.userId == '" + bureau + "' }.bureauFunction", equalTo("membre_du_bureau"));

        UUID membre = persistPerson(RoleEnum.MEMBRE);
        changeRole(membre, Map.of("role", "bureau")).statusCode(200).body("bureauFunction", equalTo("membre_du_bureau"));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void theBureauRoleCannotLeaveTheBureau() {
        UUID bureau = persistPerson(RoleEnum.BUREAU);
        setFunction(bureau, null).statusCode(400);
        assertEquals(BureauFunctionEnum.MEMBRE_DU_BUREAU, functionOf(bureau));
    }

    // --- Président ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void aNewPresidentLeavesThePreviousOneOnTheBureau() {
        UUID first = persistPerson(RoleEnum.BUREAU);
        UUID second = persistPerson(RoleEnum.BUREAU);

        setFunction(first, "president").statusCode(200).body("bureauFunction", equalTo("president"));
        setFunction(second, "president").statusCode(200);

        assertEquals(BureauFunctionEnum.MEMBRE_DU_BUREAU, functionOf(first));
        assertEquals(BureauFunctionEnum.PRESIDENT, functionOf(second));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void onlyTheBureauAndTheAdminsSitOnTheBureau() {
        UUID membre = persistPerson(RoleEnum.MEMBRE);
        setFunction(membre, "president").statusCode(400);
        setFunction(UUID.fromString(SUPER_ADMIN_ID), "membre_du_bureau").statusCode(400);
        assertNull(functionOf(membre));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void unknownPersonIsNotFound() {
        setFunction(UUID.randomUUID(), "president").statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauCannotSetAFonction() {
        setFunction(persistPerson(RoleEnum.BUREAU), "president").statusCode(403);
    }

    // --- An Admin on the Bureau ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anotherAdminCallsAnAdminToTheBureauAndTakesThemOff() {
        UUID admin = persistPerson(RoleEnum.ADMIN);
        assertNull(functionOf(admin));

        setFunction(admin, "president").statusCode(200).body("role", equalTo("admin")).body("bureauFunction", equalTo("president"));
        setFunction(admin, null).statusCode(200).body("bureauFunction", nullValue());
        assertNull(functionOf(admin));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void nobodySetsTheirOwnFonction() {
        setFunction(UUID.fromString(ADMIN_ID), "president").statusCode(403);
        assertNull(functionOf(UUID.fromString(ADMIN_ID)));
    }

    // --- Role changes ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void droppingBelowTheBureauRoleLeavesTheBureau() {
        UUID president = persistPerson(RoleEnum.BUREAU);
        setFunction(president, "president").statusCode(200);

        changeRole(president, Map.of("role", "membre")).statusCode(200).body("bureauFunction", nullValue());
        assertNull(functionOf(president));
    }

    @Test
    @TestSecurity(user = "super_admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theFonctionIsKeptBetweenBureauAndAdmin() {
        UUID president = persistPerson(RoleEnum.BUREAU);
        setFunction(president, "president").statusCode(200);

        changeRole(president, Map.of("role", "admin", "sectorId", sector.toString())).statusCode(200)
                .body("bureauFunction", equalTo("president"));
        changeRole(president, Map.of("role", "bureau")).statusCode(200).body("bureauFunction", equalTo("president"));
    }

    @Test
    @TestSecurity(user = "super_admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void anAdminOffTheBureauReachesItAsBureau() {
        UUID admin = persistPerson(RoleEnum.ADMIN);
        changeRole(admin, Map.of("role", "bureau")).statusCode(200).body("bureauFunction", equalTo("membre_du_bureau"));
    }

    @Test
    @TestSecurity(user = "super_admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void anAdminAppointedToAnotherSecteurLeavesTheirBureau() {
        UUID admin = persistPerson(RoleEnum.ADMIN);
        setFunction(admin, "president").statusCode(200);
        UUID other = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Other Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        try {
            changeRole(admin, Map.of("role", "admin", "sectorId", other.toString())).statusCode(200)
                    .body("bureauFunction", nullValue());
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                userRepository.deleteById(admin);
                created.remove(admin);
                sectorRepository.deleteById(other);
            });
        }
    }
}
