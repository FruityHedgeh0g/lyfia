package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.GroupKindEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every sign-up is at a Poste (conducteur, passager, chauffeur camionnette, point fixe, service), and a Groupe is
 * de motards or de service: only people whose Poste fits its kind are with it, and a point fixe is with none.
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class PosteResourceTest {

    static final String RIDER_ID = "00000000-0000-0000-0013-000000000001";
    static final String OTHER_ID = "00000000-0000-0000-0013-000000000002";
    static final String CHEF_ID = "00000000-0000-0000-0013-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0013-000000000004";
    static final String SERVICE_CHEF_ID = "00000000-0000-0000-0013-000000000005";

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
    private UUID sector;
    private UUID motards;
    private UUID buvette;
    private UUID event;

    @BeforeEach
    void seed() {
        persistPerson(RIDER_ID, RoleEnum.BENEVOLE, "Rider");
        persistPerson(OTHER_ID, RoleEnum.BENEVOLE, "Autre");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        persistPerson(SERVICE_CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Buvette");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            GroupEntity m = GroupEntity.builder().name("Test Motards " + UUID.randomUUID()).sector(s).build();
            groupRepository.persist(m);
            motards = m.getGroupId();
            GroupEntity b = GroupEntity.builder().name("Test Buvette " + UUID.randomUUID()).sector(s).kind(GroupKindEnum.SERVICE).build();
            groupRepository.persist(b);
            buvette = b.getGroupId();
        });
        event = persistEvent();
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId = ?1 and conducteur is not null", sector);
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            groupRepository.delete("sector.sectorId", sector);
            sectorRepository.deleteById(sector);
        });
        QuarkusTransaction.requiringNew().run(() -> persons.forEach(userRepository::deleteById));
        persons.clear();
    }

    private void persistPerson(String id, RoleEnum role, String lastName) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(userId).firstName("Test").lastName(lastName).role(role).phone("06 00 00 00 00").build()
        ));
        persons.add(userId);
    }

    private UUID persistEvent() {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1).plusHours(4));
            e.setSector(sectorRepository.findById(sector));
            eventRepository.persist(e);
            return e.getEventId();
        });
    }

    private void signedUp(String personId, PosteEnum poste, UUID groupId) {
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.signUp(
                    eventRepository.findById(event), userRepository.findById(UUID.fromString(personId)), poste, false);
            registrationRepository.persist(r);
            if (groupId != null) r.placeIn(groupRepository.findById(groupId));
        });
    }

    private EventRegistrationEntity registrationOf(String personId) {
        return QuarkusTransaction.requiringNew().call(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(personId)).orElseThrow());
    }

    private ValidatableResponse signUp(String poste, UUID groupId) {
        var request = given().queryParam("poste", poste);
        if (groupId != null) request = request.queryParam("groupId", groupId.toString());
        return request.when().put("/{id}/registration", event).then();
    }

    private ValidatableResponse changePoste(String personId, String poste) {
        return given().contentType(ContentType.JSON).body(Map.of("poste", poste))
                .when().put("/{id}/roster/{person}/poste", event, personId).then();
    }

    private String entryOf(String personId) {
        return "participants.find { it.personId == '" + personId + "' }";
    }

    private void lead(UUID groupId, String chefId) {
        given().basePath("/api").when().put("/groups/{id}/chef/{chef}", groupId, chefId).then().statusCode(200);
    }

    private ValidatableResponse changeKind(UUID groupId, String kind, String name) {
        return given().basePath("/api").contentType(ContentType.JSON)
                .body(Map.of("groupId", groupId.toString(), "name", name, "kind", kind))
                .when().patch("/groups").then();
    }

    // --- Signing up at a Poste ---

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void oneSignsUpAtAnyPosteButPassagerWithoutAConducteur() {
        signUp("chauffeur_camionnette", null).statusCode(200)
                .body("poste", equalTo("chauffeur_camionnette"))
                .body("status", equalTo("participant"));
    }

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void anUnknownPosteIsRefused() {
        signUp("pilote", null).statusCode(400);
    }

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void aServiceAsksOnlyForAGroupeDeService() {
        signUp("service", motards).statusCode(400);
        signUp("service", buvette).statusCode(200)
                .body("demande.group.groupId", equalTo(buvette.toString()));
    }

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void aConducteurAsksOnlyForAGroupeDeMotards() {
        signUp("conducteur", buvette).statusCode(400);
        signUp("chauffeur_camionnette", motards).statusCode(200)
                .body("demande.group.groupId", equalTo(motards.toString()));
    }

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void aPointFixeIsWithNoGroupe() {
        signUp("point_fixe", motards).statusCode(400);
        signUp("point_fixe", null).statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("groupId", buvette.toString()))
                .when().put("/{id}/registration/demande", event).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "rider", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = RIDER_ID))
    void onlyAConducteurIsNamedAsSomeonesConducteur() {
        signedUp(OTHER_ID, PosteEnum.CHAUFFEUR_CAMIONNETTE, null);
        given().when().get("/{id}/conducteurs", event).then().statusCode(200).body("size()", equalTo(0));
        given().queryParam("conducteurId", OTHER_ID).when().put("/{id}/registration", event).then().statusCode(400);
    }

    // --- The Bureau changes a Poste ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aPosteNoLongerFittingTheGroupeLeavesIt() {
        signedUp(RIDER_ID, PosteEnum.CONDUCTEUR, motards);

        changePoste(RIDER_ID, "chauffeur_camionnette").statusCode(200)
                .body(entryOf(RIDER_ID) + ".poste", equalTo("chauffeur_camionnette"))
                .body(entryOf(RIDER_ID) + ".group.groupId", equalTo(motards.toString()));
        changePoste(RIDER_ID, "service").statusCode(200)
                .body(entryOf(RIDER_ID) + ".poste", equalTo("service"))
                .body(entryOf(RIDER_ID) + ".group", nullValue());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauMakesNobodyAPassager() {
        signedUp(RIDER_ID, PosteEnum.CONDUCTEUR, null);
        changePoste(RIDER_ID, "passager").statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aConducteurWithAPassagerKeepsTheirPoste() {
        signedUp(RIDER_ID, PosteEnum.CONDUCTEUR, null);
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.passager(
                registrationRepository.findByEventAndPerson(event, UUID.fromString(RIDER_ID)).orElseThrow(),
                userRepository.findById(UUID.fromString(OTHER_ID)))));

        changePoste(RIDER_ID, "point_fixe").statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theChefLeadingTheGroupeKeepsTheirPoste() {
        lead(motards, CHEF_ID);
        changePoste(CHEF_ID, "point_fixe").statusCode(400);
    }

    // --- Groupes de service and their Chef ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theChefOfAGroupeDeServiceIsThereAsService() {
        lead(buvette, SERVICE_CHEF_ID);
        assertEquals(PosteEnum.SERVICE, registrationOf(SERVICE_CHEF_ID).getPoste());
        lead(motards, CHEF_ID);
        assertEquals(PosteEnum.CONDUCTEUR, registrationOf(CHEF_ID).getPoste());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aGroupeChangesKindOnlyWithNobodyButItsChef() {
        lead(motards, CHEF_ID);
        signedUp(RIDER_ID, PosteEnum.CONDUCTEUR, motards);
        String name = QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(motards).getName());

        changeKind(motards, "service", name).statusCode(400);
        assertEquals(GroupKindEnum.MOTARDS, QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(motards).getKind()));

        QuarkusTransaction.requiringNew().run(() -> registrationRepository.delete("person.userId", UUID.fromString(RIDER_ID)));
        changeKind(motards, "service", name).statusCode(200).body("kind", equalTo("service"));
        assertEquals(PosteEnum.SERVICE, registrationOf(CHEF_ID).getPoste());
    }
}
