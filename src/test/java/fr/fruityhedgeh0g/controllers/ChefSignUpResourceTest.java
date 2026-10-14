package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RideModeEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import fr.fruityhedgeh0g.services.AffectationChange;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Chef leading a Groupe rides with it at every Event of its Secteur from its Planification on, until the Event
 * is under way: a confirmed conducteur place beyond any maximum, which only a change of Affectation takes away (ADR 0010).
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class ChefSignUpResourceTest {

    static final String CHEF_ID = "00000000-0000-0000-0017-000000000001";
    static final String OTHER_CHEF_ID = "00000000-0000-0000-0017-000000000002";
    static final String NEW_CHEF_ID = "00000000-0000-0000-0017-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0017-000000000004";
    static final String MEMBRE_ID = "00000000-0000-0000-0017-000000000005";
    static final String CONDUCTEUR_ID = "00000000-0000-0000-0017-000000000006";

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
    Event<AffectationChange> affectationChanges;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;
    private UUID nord;
    private UUID sud;

    @BeforeEach
    void seed() {
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(OTHER_CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Autre");
        persistPerson(NEW_CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Nouveau");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        persistPerson(MEMBRE_ID, RoleEnum.MEMBRE, "Membre");
        persistPerson(CONDUCTEUR_ID, RoleEnum.BENEVOLE, "Conducteur");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            nord = persistGroup("Test Nord " + UUID.randomUUID(), s, CHEF_ID);
            sud = persistGroup("Test Sud " + UUID.randomUUID(), s, OTHER_CHEF_ID);
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("conducteur is not null and event.sector.sectorId = ?1", sector);
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.delete("sector.sectorId", sector);
            groupRepository.delete("sector.sectorId", sector);
            sectorRepository.deleteById(sector);
            persons.forEach(userRepository::deleteById);
        });
        persons.clear();
    }

    private void persistPerson(String id, RoleEnum role, String lastName) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(UUID.fromString(id)).firstName("Test").lastName(lastName).role(role).build()));
        persons.add(UUID.fromString(id));
    }

    private UUID persistGroup(String name, SectorEntity s, String chefId) {
        GroupEntity group = GroupEntity.builder().name(name).sector(s).chef(userRepository.findById(UUID.fromString(chefId))).build();
        groupRepository.persist(group);
        return group.getGroupId();
    }

    /** An Event of the Secteur, its Chefs signed up as when it was created (the same Affectation sync). */
    private UUID eventIn(EventStatusEnum status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(status);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(10));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(11));
            e.setSector(sectorRepository.findById(sector));
            eventRepository.persist(e);
            if (status != EventStatusEnum.ANNULE) {
                affectationChanges.fire(new AffectationChange(nord, null));
                affectationChanges.fire(new AffectationChange(sud, null));
            }
            return e.getEventId();
        });
    }

    private Optional<EventRegistrationEntity> registrationOf(UUID event, String personId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Optional<EventRegistrationEntity> found = registrationRepository.findByEventAndPerson(event, UUID.fromString(personId));
            found.ifPresent(r -> {
                if (r.getGroup() != null) r.getGroup().getGroupId();
            });
            return found;
        });
    }

    private UUID groupOf(UUID event, String personId) {
        return registrationOf(event, personId).map(r -> r.getGroup() == null ? null : r.getGroup().getGroupId()).orElse(null);
    }

    // --- Signed up from the Planification ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void creatingAnEventSignsUpEachGroupesChefBeyondTheMaximum() {
        String event = given().contentType(ContentType.JSON)
                .body(Map.of("name", "Test Nouvelle", "sectorId", sector.toString(), "maxParticipants", 1,
                        "startDateTime", "2031-01-01T10:00:00", "endDateTime", "2031-01-01T18:00:00"))
                .when().post().then().statusCode(200).extract().path("eventId");

        given().when().get("/{id}/roster", event).then().statusCode(200)
                .body("participants.personId", hasItems(CHEF_ID, OTHER_CHEF_ID))
                .body("waiting.size()", equalTo(0))
                .body("participants.find { it.personId == '" + CHEF_ID + "' }.mode", equalTo("conducteur"))
                .body("participants.find { it.personId == '" + CHEF_ID + "' }.group.groupId", equalTo(nord.toString()))
                .body("participants.find { it.personId == '" + CHEF_ID + "' }.leadsGroup", equalTo(true));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void theChefSeesTheirGroupesEventFromItsPlanification() {
        UUID event = eventIn(EventStatusEnum.PLANIFICATION);

        given().when().get("/{id}", event).then().statusCode(200);
        given().when().get().then().statusCode(200).body("eventId", hasItem(event.toString()));
        given().when().get("/registrations").then().statusCode(200)
                .body("find { it.eventId == '" + event + "' }.leadsGroup", equalTo(true));
        given().when().get("/mon-groupe").then().statusCode(200).body("events.eventId", hasItem(event.toString()));
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void othersStillSeeItOnlyOnceOpen() {
        UUID event = eventIn(EventStatusEnum.PLANIFICATION);

        given().when().get("/{id}", event).then().statusCode(404);
        given().when().get().then().statusCode(200).body("eventId", not(hasItem(event.toString())));
    }

    // --- Nothing but the Affectation takes the place away ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void theChefCannotWithdraw() {
        UUID event = eventIn(EventStatusEnum.OUVERT);
        given().when().delete("/{id}/registration", event).then().statusCode(400);
        assertTrue(registrationOf(event, CHEF_ID).isPresent());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauNeitherRemovesNorMovesTheChef() {
        UUID event = eventIn(EventStatusEnum.OUVERT);
        given().when().delete("/{e}/roster/{p}", event, CHEF_ID).then().statusCode(400);
        given().when().delete("/{e}/roster/{p}/group", event, CHEF_ID).then().statusCode(400);
        given().when().put("/{e}/roster/{p}/group/{g}", event, CHEF_ID, sud).then().statusCode(400);
        assertEquals(nord, groupOf(event, CHEF_ID));
    }

    // --- Following the Affectations ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aNewChefRidesWithTheGroupeAndThePreviousOneStaysWithoutIt() {
        UUID event = eventIn(EventStatusEnum.OUVERT);

        given().basePath("/api/groups").when().put("/{g}/chef/{p}", nord, NEW_CHEF_ID).then().statusCode(200);

        assertEquals(nord, groupOf(event, NEW_CHEF_ID));
        assertTrue(registrationOf(event, CHEF_ID).isPresent());
        assertNull(groupOf(event, CHEF_ID));
        // No longer the Chef: an ordinary Participant again, who may withdraw
        given().when().delete("/{e}/roster/{p}", event, CHEF_ID).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aGroupeLeftWithoutAChefLeavesThePreviousOneSignedUpWithoutIt() {
        UUID event = eventIn(EventStatusEnum.OUVERT);

        given().basePath("/api/groups").when().delete("/{g}/chef", nord).then().statusCode(200);

        assertTrue(registrationOf(event, CHEF_ID).isPresent());
        assertNull(groupOf(event, CHEF_ID));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aNewChefAlreadyRidingAsPassagerBecomesTheConducteur() {
        UUID event = eventIn(EventStatusEnum.OUVERT);
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity conducteur = EventRegistrationEntity.conducteur(
                    eventRepository.findById(event), userRepository.findById(UUID.fromString(CONDUCTEUR_ID)), true);
            registrationRepository.persist(conducteur);
            registrationRepository.persist(EventRegistrationEntity.passager(conducteur, userRepository.findById(UUID.fromString(NEW_CHEF_ID))));
        });

        given().basePath("/api/groups").when().put("/{g}/chef/{p}", nord, NEW_CHEF_ID).then().statusCode(200);

        EventRegistrationEntity chef = registrationOf(event, NEW_CHEF_ID).orElseThrow();
        assertEquals(RideModeEnum.CONDUCTEUR, chef.getMode());
        assertFalse(chef.isWaiting());
        assertEquals(nord, groupOf(event, NEW_CHEF_ID));
        assertEquals(0, QuarkusTransaction.requiringNew().call(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(CONDUCTEUR_ID)).orElseThrow().getPassagers().size()));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aCancelledEventNoLongerFollowsTheAffectations() {
        UUID event = eventIn(EventStatusEnum.ANNULE);

        given().basePath("/api/groups").when().put("/{g}/chef/{p}", nord, NEW_CHEF_ID).then().statusCode(200);

        assertTrue(registrationOf(event, NEW_CHEF_ID).isEmpty());
    }
}
