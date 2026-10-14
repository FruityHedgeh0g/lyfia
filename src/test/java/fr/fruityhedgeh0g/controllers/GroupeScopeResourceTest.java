package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Bureau and Admin manage only their own Secteur's Groupes and Affectations; the Super admin all (ADR 0004). */
@QuarkusTest
@TestHTTPEndpoint(GroupController.class)
public class GroupeScopeResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0014-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0014-000000000002";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0014-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0014-000000000004";
    static final String OTHER_CHEF_ID = "00000000-0000-0000-0014-000000000005";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    GroupRepository groupRepository;

    private final List<UUID> persons = new ArrayList<>();
    private UUID algrange;
    private UUID thionville;
    private UUID ours;
    private UUID theirs;

    @BeforeEach
    void seed() {
        algrange = persistSector("Test Algrange ");
        thionville = persistSector("Test Thionville ");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, algrange);
        persistPerson(ADMIN_ID, RoleEnum.ADMIN, algrange);
        persistPerson(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN, null);
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, algrange);
        persistPerson(OTHER_CHEF_ID, RoleEnum.CHEF_DE_GROUPE, thionville);
        ours = persistGroup("Test Nord ", algrange);
        theirs = persistGroup("Test Sud ", thionville);
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            groupRepository.delete("sector.sectorId in ?1", List.of(algrange, thionville));
            persons.forEach(userRepository::deleteById);
            sectorRepository.deleteById(algrange);
            sectorRepository.deleteById(thionville);
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
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(userId).firstName("Test").lastName(role.name()).role(role)
                        .sector(sectorId == null ? null : sectorRepository.findById(sectorId)).build()));
        persons.add(userId);
    }

    private UUID persistGroup(String name, UUID sectorId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            GroupEntity g = GroupEntity.builder().name(name + UUID.randomUUID()).sector(sectorRepository.findById(sectorId)).build();
            groupRepository.persist(g);
            return g.getGroupId();
        });
    }

    private ValidatableResponse create(UUID sectorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Nouveau " + UUID.randomUUID());
        body.put("description", "");
        body.put("area", "");
        if (sectorId != null) body.put("sectorId", sectorId.toString());
        return given().contentType(ContentType.JSON).body(body).when().post().then();
    }

    private ValidatableResponse rename(UUID groupId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("groupId", groupId.toString(), "name", "Test Renommé " + UUID.randomUUID()))
                .when().patch().then();
    }

    private UUID sectorOf(UUID groupId) {
        return QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(groupId).getSector().getSectorId());
    }

    // --- Creating ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauCreatesGroupesInItsOwnSecteurOnly() {
        UUID created = UUID.fromString(create(null).statusCode(200).extract().path("groupId"));
        assertEquals(algrange, sectorOf(created));
        create(algrange).statusCode(200);
        create(thionville).statusCode(403);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminChoosesTheGroupesSecteur() {
        create(null).statusCode(400);
        UUID created = UUID.fromString(create(thionville).statusCode(200).extract().path("groupId"));
        assertEquals(thionville, sectorOf(created));
    }

    @Test
    void everyoneReadsEachGroupesSecteur() {
        given().when().get().then().statusCode(200)
                .body("find { it.groupId == '" + ours + "' }.sectorId", equalTo(algrange.toString()));
    }

    // --- Editing and Affectations ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminEditsOnlyTheirSecteursGroupes() {
        rename(ours).statusCode(200);
        rename(theirs).statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauMakesAffectationsOnlyInItsSecteur() {
        given().when().put("/{g}/chef/{u}", ours, CHEF_ID).then().statusCode(200);
        given().when().put("/{g}/chef/{u}", theirs, OTHER_CHEF_ID).then().statusCode(403);
        given().when().delete("/{g}/chef", theirs).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void aGroupeIsLedByAChefOfItsOwnSecteur() {
        given().when().put("/{g}/chef/{u}", ours, OTHER_CHEF_ID).then().statusCode(400);
        given().when().put("/{g}/chef/{u}", theirs, OTHER_CHEF_ID).then().statusCode(200);
    }

    // --- Secteur side ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauEditsOnlyItsOwnSecteursDescription() {
        String name = QuarkusTransaction.requiringNew().call(() -> sectorRepository.findById(thionville).getName());
        given().basePath("/api/sectors").contentType(ContentType.JSON)
                .body(Map.of("sectorId", thionville.toString(), "name", name, "description", "Piraté"))
                .when().patch("/").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauPlacesNoGroupeInAnotherSecteur() {
        UUID loose = QuarkusTransaction.requiringNew().call(() -> {
            GroupEntity g = GroupEntity.builder().name("Test Libre " + UUID.randomUUID()).build();
            groupRepository.persist(g);
            return g.getGroupId();
        });
        try {
            given().basePath("/api/sectors").when().put("/{s}/group/{g}", thionville, loose).then().statusCode(403);
        } finally {
            QuarkusTransaction.requiringNew().run(() -> groupRepository.deleteById(loose));
        }
    }
}
