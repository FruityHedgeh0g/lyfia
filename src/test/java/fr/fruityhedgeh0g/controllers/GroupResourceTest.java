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
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Groupes: area, Affectation of a Chef de groupe, public listing. */
@QuarkusTest
@TestHTTPEndpoint(GroupController.class)
public class GroupResourceTest {

    static final String MEMBRE_ID = "00000000-0000-0000-0003-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0003-000000000005";
    static final String ADMIN_ID = "00000000-0000-0000-0003-000000000006";

    @Inject
    UserRepository userRepository;

    @Inject
    GroupRepository groupRepository;

    @Inject
    SectorRepository sectorRepository;

    private final List<UUID> persons = new ArrayList<>();
    private final List<UUID> groups = new ArrayList<>();
    /** Everyone from Membre up belongs to this Secteur (ADR 0004). */
    private UUID sector;

    @BeforeEach
    void seedActors() {
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        persistPerson(UUID.fromString(MEMBRE_ID), RoleEnum.MEMBRE);
        persistPerson(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU);
        persistPerson(UUID.fromString(ADMIN_ID), RoleEnum.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            groupRepository.list("name like 'Test %'").forEach(groupRepository::delete);
            groups.forEach(groupRepository::deleteById);
            groupRepository.delete("sector.sectorId", sector);
            persons.forEach(userRepository::deleteById);
            sectorRepository.deleteById(sector);
        });
        groups.clear();
        persons.clear();
    }

    private UUID persistPerson(UUID id, RoleEnum role) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(id).firstName("Prénom").lastName(role.name()).role(role)
                        .sector(role.isAtLeast(RoleEnum.MEMBRE) ? sectorRepository.findById(sector) : null).build()
        ));
        persons.add(id);
        return id;
    }

    private UUID persistPerson(RoleEnum role) {
        return persistPerson(UUID.randomUUID(), role);
    }

    private UUID persistGroup(String name) {
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            GroupEntity group = GroupEntity.builder().name(name).description("Desc").area("Nord")
                    .sector(sectorRepository.findById(sector)).build();
            groupRepository.persist(group);
            return group.getGroupId();
        });
        groups.add(id);
        return id;
    }

    private UUID chefOf(UUID groupId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity chef = groupRepository.findById(groupId).getChef();
            return chef == null ? null : chef.getUserId();
        });
    }

    // --- Public listing ---

    @Test
    void anonymousVisiteurListsGroupesWithAreaAndChef() {
        UUID chef = persistPerson(RoleEnum.CHEF_DE_GROUPE);
        UUID group = persistGroup("Test Nord");
        QuarkusTransaction.requiringNew().run(() ->
                groupRepository.findById(group).setChef(userRepository.findById(chef)));

        given().when().get("/").then().statusCode(200)
                .body("find { it.groupId == '" + group + "' }.description", equalTo("Desc"))
                .body("find { it.groupId == '" + group + "' }.area", equalTo("Nord"))
                .body("find { it.groupId == '" + group + "' }.chef.lastName", equalTo("CHEF_DE_GROUPE"));
    }

    @Test
    void anonymousVisiteurCannotCreateAGroupe() {
        given().contentType(ContentType.JSON).body(Map.of("name", "Test Anonyme"))
                .when().post("/").then().statusCode(401);
    }

    // --- Create and edit ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureauCreatesAndEditsAGroupe() {
        String id = given().contentType(ContentType.JSON)
                .body(Map.of("name", "Test Sud", "description", "Les motards du sud", "area", "Sud du secteur"))
                .when().post("/").then().statusCode(200)
                .extract().path("groupId");

        given().contentType(ContentType.JSON)
                .body(Map.of("groupId", id, "name", "Test Sud-Est", "area", "Sud-est du secteur"))
                .when().patch("/").then().statusCode(200)
                .body("name", equalTo("Test Sud-Est"))
                .body("description", equalTo("Les motards du sud"))
                .body("area", equalTo("Sud-est du secteur"));
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void membreCannotCreateOrEdit() {
        UUID group = persistGroup("Test Membre");
        given().contentType(ContentType.JSON).body(Map.of("name", "Test Refusé"))
                .when().post("/").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("groupId", group.toString(), "name", "Test Refusé"))
                .when().patch("/").then().statusCode(403);
    }

    // --- Affectation ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureauSetsAndClearsAChef() {
        UUID group = persistGroup("Test Affectation");
        UUID chef = persistPerson(RoleEnum.CHEF_DE_GROUPE);

        given().when().put("/{g}/chef/{p}", group, chef).then().statusCode(200)
                .body("chef.userId", equalTo(chef.toString()));
        assertEquals(chef, chefOf(group));

        given().when().delete("/{g}/chef", group).then().statusCode(200)
                .body("chef", nullValue());
        assertNull(chefOf(group));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aBureauMemberCanLeadAGroupe() {
        UUID group = persistGroup("Test Bureau");
        given().when().put("/{g}/chef/{p}", group, BUREAU_ID).then().statusCode(200);
        assertEquals(UUID.fromString(BUREAU_ID), chefOf(group));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void onlyAChefDeGroupeOrAboveCanLead() {
        UUID group = persistGroup("Test Refus");
        given().when().put("/{g}/chef/{p}", group, persistPerson(RoleEnum.MEMBRE)).then().statusCode(400);
        given().when().put("/{g}/chef/{p}", group, persistPerson(RoleEnum.BENEVOLE)).then().statusCode(400);
        assertNull(chefOf(group));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void givingAChefASecondGroupeFreesTheFirst() {
        UUID first = persistGroup("Test Premier");
        UUID second = persistGroup("Test Second");
        UUID chef = persistPerson(RoleEnum.CHEF_DE_GROUPE);

        given().when().put("/{g}/chef/{p}", first, chef).then().statusCode(200);
        given().when().put("/{g}/chef/{p}", second, chef).then().statusCode(200);

        assertNull(chefOf(first));
        assertEquals(chef, chefOf(second));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void handingAGroupeToANewChefReplacesThePreviousOne() {
        UUID group = persistGroup("Test Relève");
        UUID previous = persistPerson(RoleEnum.CHEF_DE_GROUPE);
        UUID next = persistPerson(RoleEnum.CHEF_DE_GROUPE);

        given().when().put("/{g}/chef/{p}", group, previous).then().statusCode(200);
        given().when().put("/{g}/chef/{p}", group, next).then().statusCode(200);

        assertEquals(next, chefOf(group));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void unknownGroupeOrPersonIsNotFound() {
        UUID group = persistGroup("Test Inconnu");
        given().when().put("/{g}/chef/{p}", UUID.randomUUID(), BUREAU_ID).then().statusCode(404);
        given().when().put("/{g}/chef/{p}", group, UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void membreCannotMakeAnAffectation() {
        UUID group = persistGroup("Test Membre Affectation");
        given().when().put("/{g}/chef/{p}", group, BUREAU_ID).then().statusCode(403);
        given().when().delete("/{g}/chef", group).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void revokingChefDeGroupeEndsTheAffectation() {
        UUID group = persistGroup("Test Révocation");
        UUID chef = persistPerson(RoleEnum.CHEF_DE_GROUPE);
        given().when().put("/{g}/chef/{p}", group, chef).then().statusCode(200);

        given().basePath("/api/users").contentType(ContentType.JSON).body(Map.of("role", "membre"))
                .when().put("/{id}/role", chef).then().statusCode(200);

        assertNull(chefOf(group));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void promotingAChefToTheBureauKeepsTheAffectation() {
        UUID group = persistGroup("Test Promotion");
        UUID chef = persistPerson(RoleEnum.CHEF_DE_GROUPE);
        given().when().put("/{g}/chef/{p}", group, chef).then().statusCode(200);

        given().basePath("/api/users").contentType(ContentType.JSON).body(Map.of("role", "bureau"))
                .when().put("/{id}/role", chef).then().statusCode(200);

        assertEquals(chef, chefOf(group));
    }
}
