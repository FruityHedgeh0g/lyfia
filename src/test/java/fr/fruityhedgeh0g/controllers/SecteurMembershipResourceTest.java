package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakRoleMirror;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** From Membre up a person belongs to one Secteur, given by whoever promotes them (ADR 0004). */
@QuarkusTest
@TestHTTPEndpoint(UserController.class)
public class SecteurMembershipResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0013-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0013-000000000002";
    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0013-000000000003";

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
        persistPerson(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU, algrange);
        persistPerson(UUID.fromString(ADMIN_ID), RoleEnum.ADMIN, algrange);
        persistPerson(UUID.fromString(SUPER_ADMIN_ID), RoleEnum.SUPER_ADMIN, null);
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            List<UUID> sectors = List.of(algrange, thionville);
            groupRepository.delete("sector.sectorId in ?1", sectors);
            registrationRepository.delete("event.sector.sectorId in ?1", sectors);
            eventRepository.list("sector.sectorId in ?1", sectors).forEach(eventRepository::delete);
            persons.forEach(userRepository::deleteById);
            eventRepository.flush();
            sectorRepository.deleteById(algrange);
            sectorRepository.deleteById(thionville);
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

    private UUID persistPerson(UUID id, RoleEnum role, UUID sectorId) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(id).firstName("Test").lastName(role.name()).role(role)
                        .sector(sectorId == null ? null : sectorRepository.findById(sectorId)).build()));
        persons.add(id);
        return id;
    }

    private UUID person(RoleEnum role, UUID sectorId) {
        return persistPerson(UUID.randomUUID(), role, sectorId);
    }

    /** Signs the person up for a past Event of the Secteur: riding with a Secteur once is enough. */
    private void rodeWith(UUID personId, UUID sectorId) {
        QuarkusTransaction.requiringNew().run(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(10));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(9));
            e.setSector(sectorRepository.findById(sectorId));
            eventRepository.persist(e);
            registrationRepository.persist(EventRegistrationEntity.conducteur(e, userRepository.findById(personId), false));
        });
    }

    private ValidatableResponse setRole(UUID personId, String role, UUID sectorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", role);
        if (sectorId != null) body.put("sectorId", sectorId.toString());
        return given().contentType(ContentType.JSON).body(body).when().put("/{id}/role", personId).then();
    }

    private UUID storedSector(UUID personId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = userRepository.findById(personId).getSector();
            return s == null ? null : s.getSectorId();
        });
    }

    // --- Bureau ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aNewMembreJoinsThePromotersSecteur() {
        UUID benevole = person(RoleEnum.BENEVOLE, null);
        rodeWith(benevole, algrange);
        setRole(benevole, "membre", null).statusCode(200).body("sector.sectorId", equalTo(algrange.toString()));
        assertEquals(algrange, storedSector(benevole));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void goingBackToBenevoleLeavesTheSecteur() {
        UUID membre = person(RoleEnum.MEMBRE, algrange);
        setRole(membre, "benevole", null).statusCode(200).body("sector", nullValue());
        assertNull(storedSector(membre));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauActsOnlyOnItsOwnSecteursPeople() {
        UUID other = person(RoleEnum.MEMBRE, thionville);
        setRole(other, "chef_de_groupe", null).statusCode(403);
        setRole(other, "benevole", null).statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void onlyTheSuperAdminNamesASecteur() {
        UUID benevole = person(RoleEnum.BENEVOLE, null);
        setRole(benevole, "membre", thionville).statusCode(403);
    }

    // --- Admin ---

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminPromotesToBureauOnlyInTheirSecteur() {
        setRole(person(RoleEnum.MEMBRE, algrange), "bureau", null).statusCode(200);
        setRole(person(RoleEnum.MEMBRE, thionville), "bureau", null).statusCode(403);
    }

    // --- Super admin ---

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminAppointsAnAdminFromAnyoneForTheSecteurTheyChoose() {
        UUID benevole = person(RoleEnum.BENEVOLE, null);
        setRole(benevole, "admin", null).statusCode(400);
        setRole(benevole, "admin", thionville).statusCode(200).body("sector.sectorId", equalTo(thionville.toString()));
        assertEquals(thionville, storedSector(benevole));
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void appointingAnAdminOfAnotherSecteurEndsTheirAffectation() {
        UUID chef = person(RoleEnum.CHEF_DE_GROUPE, algrange);
        UUID groupe = QuarkusTransaction.requiringNew().call(() -> {
            GroupEntity g = GroupEntity.builder().name("Test Nord " + UUID.randomUUID())
                    .sector(sectorRepository.findById(algrange)).chef(userRepository.findById(chef)).build();
            groupRepository.persist(g);
            return g.getGroupId();
        });

        setRole(chef, "admin", thionville).statusCode(200);
        assertEquals(thionville, storedSector(chef));
        assertNull(QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(groupe).getChef()));
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminNamesTheSecteurOfANewMembre() {
        UUID benevole = person(RoleEnum.BENEVOLE, null);
        setRole(benevole, "membre", null).statusCode(400);
        setRole(benevole, "membre", algrange).statusCode(200);
        assertEquals(algrange, storedSector(benevole));
    }

    @Test
    @TestSecurity(user = "super-admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void aSecteurFermeGetsNobody() {
        QuarkusTransaction.requiringNew().run(() -> sectorRepository.findById(thionville).setClosed(true));
        setRole(person(RoleEnum.BENEVOLE, null), "admin", thionville).statusCode(400);
    }

    // --- Current person ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theCurrentPersonSeesTheirSecteur() {
        given().when().get("/me").then().statusCode(200).body("sector.sectorId", equalTo(algrange.toString()));
    }
}
