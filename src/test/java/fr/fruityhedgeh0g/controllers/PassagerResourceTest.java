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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A passager signs up with a conducteur of the same Event, counts towards every maximum and always follows their conducteur. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class PassagerResourceTest {

    static final String CONDUCTEUR_ID = "00000000-0000-0000-0009-000000000001";
    static final String PASSAGER_ID = "00000000-0000-0000-0009-000000000002";
    static final String OTHER_ID = "00000000-0000-0000-0009-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0009-000000000004";
    static final String BUREAU_ID = "00000000-0000-0000-0009-000000000005";

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
    private UUID nord;
    private UUID event;

    @BeforeEach
    void seed() {
        persistPerson(CONDUCTEUR_ID, RoleEnum.BENEVOLE, "Conducteur");
        persistPerson(PASSAGER_ID, RoleEnum.BENEVOLE, "Passager");
        persistPerson(OTHER_ID, RoleEnum.BENEVOLE, "Autre");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            GroupEntity group = GroupEntity.builder().name("Test Nord " + UUID.randomUUID()).sector(s)
                    .chef(userRepository.findById(UUID.fromString(CHEF_ID))).build();
            groupRepository.persist(group);
            nord = group.getGroupId();
        });
        event = persistEvent(null);
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            // Passagers first: they point at their conducteur's sign-up
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

    private UUID persistEvent(Integer max) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
            e.setMaxParticipants(max);
            e.setSector(sectorRepository.findById(sector));
            eventRepository.persist(e);
            return e.getEventId();
        });
    }

    private void maximumIs(Integer max) {
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).setMaxParticipants(max));
    }

    private void nordMaximumIs(int max) {
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).getGroupMaximums().put(nord, max));
    }

    /** Signs a conducteur up directly in the database. */
    private void conducteurSignedUp(String personId, UUID eventId, boolean waiting) {
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.conducteur(
                eventRepository.findById(eventId), userRepository.findById(UUID.fromString(personId)), waiting)));
    }

    private void conducteurSignedUp(boolean waiting) {
        conducteurSignedUp(CONDUCTEUR_ID, event, waiting);
    }

    /** Signs the passager up with the conducteur directly in the database, mirroring the conducteur. */
    private void passagerSignedUp() {
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.passager(
                registrationOf(CONDUCTEUR_ID), userRepository.findById(UUID.fromString(PASSAGER_ID)))));
    }

    private EventRegistrationEntity registrationOf(String personId) {
        return registrationRepository.findByEventAndPerson(event, UUID.fromString(personId)).orElseThrow();
    }

    private void conducteurAsksForNord() {
        QuarkusTransaction.requiringNew().run(() -> registrationOf(CONDUCTEUR_ID).requestGroup(groupRepository.findById(nord)));
    }

    private void conducteurRidesWithNord() {
        conducteurAsksForNord();
        QuarkusTransaction.requiringNew().run(() -> registrationOf(CONDUCTEUR_ID).acceptDemande());
    }

    private ValidatableResponse signUpAsPassagerOf(String conducteurId) {
        return given().queryParam("conducteurId", conducteurId).when().put("/{id}/registration", event).then();
    }

    private ValidatableResponse roster() {
        return given().when().get("/{id}/roster", event).then().statusCode(200);
    }

    private String entryOf(String list, String personId) {
        return list + ".find { it.personId == '" + personId + "' }";
    }

    private boolean signedUp(String personId) {
        return QuarkusTransaction.requiringNew().call(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(personId)).isPresent());
    }

    // --- Signing up as passager ---

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerSignsUpWithTheirConducteurAndFollowsTheirGroupe() {
        conducteurSignedUp(false);
        conducteurRidesWithNord();

        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(200)
                .body("poste", equalTo("passager"))
                .body("status", equalTo("participant"))
                .body("conducteur.userId", equalTo(CONDUCTEUR_ID))
                .body("conducteur.lastName", equalTo("Conducteur"))
                .body("group.groupId", equalTo(nord.toString()));
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerNeedsAConducteurSignedUpForTheSameEvent() {
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);

        conducteurSignedUp(CONDUCTEUR_ID, persistEvent(null), false);
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
        assertTrue(!signedUp(PASSAGER_ID));
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerOfAPassagerIsRefused() {
        conducteurSignedUp(OTHER_ID, event, false);
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persist(EventRegistrationEntity.passager(
                registrationOf(OTHER_ID), userRepository.findById(UUID.fromString(CONDUCTEUR_ID)))));

        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerCannotAlsoAskForAGroupe() {
        conducteurSignedUp(false);
        given().queryParam("conducteurId", CONDUCTEUR_ID).queryParam("groupId", nord.toString())
                .when().put("/{id}/registration", event).then().statusCode(400);

        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("groupId", nord.toString()))
                .when().put("/{id}/registration/demande", event).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void thePassagerOfAWaitingConducteurWaitsToo() {
        conducteurSignedUp(true);
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(200).body("status", equalTo("en_attente"));
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void anyBenevoleSeesTheConducteursOfAnEvent() {
        conducteurSignedUp(false);
        given().when().get("/{id}/conducteurs", event).then().statusCode(200)
                .body("userId", contains(CONDUCTEUR_ID))
                .body("[0].lastName", equalTo("Conducteur"))
                .body("[0].phone", nullValue());
    }

    // --- Maximums ---

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerCannotJoinAConducteurWhenTheEventIsAtItsMaximum() {
        maximumIs(1);
        conducteurSignedUp(false);
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerCannotJoinAParticipantConducteurWhileTheEventIsComplet() {
        conducteurSignedUp(false);
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).setStatus(EventStatusEnum.COMPLET));
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void someoneSignedUpAsConducteurCannotTurnPassager() {
        conducteurSignedUp(false);
        conducteurSignedUp(PASSAGER_ID, event, false);
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerCannotJoinAConducteurWhoseGroupeIsAtItsMaximum() {
        conducteurSignedUp(false);
        conducteurRidesWithNord();
        nordMaximumIs(1);
        signUpAsPassagerOf(CONDUCTEUR_ID).statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aPassagerCountsTowardsTheEventMaximum() {
        maximumIs(2);
        conducteurSignedUp(false);
        passagerSignedUp();
        conducteurSignedUp(OTHER_ID, event, true);

        given().when().post("/{e}/roster/{p}/promote", event, OTHER_ID).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void aPassagerCountsTowardsTheGroupeMaximum() {
        nordMaximumIs(2);
        conducteurSignedUp(false);
        passagerSignedUp();
        conducteurAsksForNord();
        conducteurSignedUp(OTHER_ID, event, false);
        QuarkusTransaction.requiringNew().run(() -> registrationOf(OTHER_ID).placeIn(groupRepository.findById(nord)));

        // Conducteur and passager need two places; only one is left
        given().when().post("/{e}/demandes/{p}/accept", event, CONDUCTEUR_ID).then().statusCode(400);
    }

    // --- Following the conducteur ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void movingTheConducteurUpMovesThePassagerUp() {
        maximumIs(2);
        conducteurSignedUp(true);
        passagerSignedUp();

        given().when().post("/{e}/roster/{p}/promote", event, CONDUCTEUR_ID).then().statusCode(200)
                .body("participants.personId", containsInAnyOrder(CONDUCTEUR_ID, PASSAGER_ID));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theConducteurIsNotMovedUpWithoutRoomForTheirPassagers() {
        maximumIs(1);
        conducteurSignedUp(true);
        passagerSignedUp();

        given().when().post("/{e}/roster/{p}/promote", event, CONDUCTEUR_ID).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void acceptingTheConducteursDemandeTakesThePassagerIntoTheGroupe() {
        conducteurSignedUp(false);
        passagerSignedUp();
        conducteurAsksForNord();

        given().when().post("/{e}/demandes/{p}/accept", event, CONDUCTEUR_ID).then().statusCode(200);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("events.find { it.eventId == '" + event + "' }.members.personId", containsInAnyOrder(CONDUCTEUR_ID, PASSAGER_ID));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void refusingTheConducteursDemandeLeavesThePassagerWithoutAGroupe() {
        conducteurSignedUp(false);
        passagerSignedUp();
        conducteurAsksForNord();

        given().when().post("/{e}/demandes/{p}/refuse", event, CONDUCTEUR_ID).then().statusCode(200);
        assertTrue(QuarkusTransaction.requiringNew().call(() -> registrationOf(PASSAGER_ID).getGroup() == null));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void placingTheConducteurInAGroupePlacesThePassager() {
        conducteurSignedUp(false);
        passagerSignedUp();

        given().when().put("/{e}/roster/{p}/group/{g}", event, CONDUCTEUR_ID, nord).then().statusCode(200)
                .body(entryOf("participants", PASSAGER_ID) + ".group.groupId", equalTo(nord.toString()));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void takingTheConducteurOutOfTheGroupeTakesThePassagerOut() {
        conducteurSignedUp(false);
        conducteurRidesWithNord();
        passagerSignedUp();

        given().when().delete("/{e}/roster/{p}/group", event, CONDUCTEUR_ID).then().statusCode(200);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("events.find { it.eventId == '" + event + "' }.members", org.hamcrest.Matchers.empty());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aPassagerIsNotMovedWithoutTheirConducteur() {
        maximumIs(5);
        conducteurSignedUp(true);
        passagerSignedUp();
        given().when().post("/{e}/roster/{p}/promote", event, PASSAGER_ID).then().statusCode(400);

        QuarkusTransaction.requiringNew().run(() -> registrationOf(CONDUCTEUR_ID).moveUp());
        given().when().put("/{e}/roster/{p}/group/{g}", event, PASSAGER_ID, nord).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void removingTheConducteurRemovesThePassager() {
        conducteurSignedUp(false);
        passagerSignedUp();

        given().when().delete("/{e}/roster/{p}", event, CONDUCTEUR_ID).then().statusCode(200)
                .body("participants", org.hamcrest.Matchers.empty());
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void theConducteursWithdrawalWithdrawsThePassager() {
        conducteurSignedUp(false);
        passagerSignedUp();

        given().when().delete("/{id}/registration", event).then().statusCode(204);
        assertTrue(!signedUp(PASSAGER_ID));
    }

    @Test
    @TestSecurity(user = "passager", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = PASSAGER_ID))
    void aPassagerWithdrawsAloneAndTheConducteurStays() {
        conducteurSignedUp(false);
        passagerSignedUp();

        given().when().delete("/{id}/registration", event).then().statusCode(204);
        assertTrue(signedUp(CONDUCTEUR_ID));
        assertTrue(!signedUp(PASSAGER_ID));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theRosterNamesEachPassagersConducteur() {
        conducteurSignedUp(false);
        passagerSignedUp();
        roster().body(entryOf("participants", PASSAGER_ID) + ".poste", equalTo("passager"))
                .body(entryOf("participants", CONDUCTEUR_ID) + ".passagers", equalTo(1))
                .body(entryOf("participants", PASSAGER_ID) + ".conducteur.userId", equalTo(CONDUCTEUR_ID))
                .body(entryOf("participants", CONDUCTEUR_ID) + ".conducteur", nullValue());
    }
}
