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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * People sign up per Journée (ADR 0011): for some Journées or the whole Event, adding or leaving one later; each
 * Journée has its own maximum count and Liste d'attente; shortening an Event removes its dropped Journées' sign-ups.
 */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class JourneeResourceTest {

    static final String ME = "00000000-0000-0000-0014-000000000001";
    static final String OTHER_ID = "00000000-0000-0000-0014-000000000002";
    static final String CHEF_ID = "00000000-0000-0000-0014-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0014-000000000004";

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
    private LocalDate samedi;
    private LocalDate dimanche;

    @BeforeEach
    void seed() {
        persistPerson(ME, RoleEnum.BENEVOLE, "Moi");
        persistPerson(OTHER_ID, RoleEnum.BENEVOLE, "Autre");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            GroupEntity group = GroupEntity.builder().name("Test Nord " + UUID.randomUUID()).sector(s).build();
            groupRepository.persist(group);
            nord = group.getGroupId();

            EventEntity e = new EventEntity();
            e.setName("Test Week-end");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(5).withHour(9));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(6).withHour(18));
            e.setSector(s);
            eventRepository.persist(e);
            event = e.getEventId();
            samedi = e.journees().get(0);
            dimanche = e.journees().get(1);
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
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
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(userId).firstName("Test").lastName(lastName).role(role).phone("06 00 00 00 00").build()
        ));
        persons.add(userId);
    }

    private ValidatableResponse signUpFor(LocalDate... journees) {
        var request = given();
        for (LocalDate journee : journees) request = request.queryParam("journee", journee.toString());
        return request.when().put("/{id}/registration", event).then();
    }

    private void maximumIs(int max) {
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).setMaxParticipants(max));
    }

    /** Signs someone up directly, confirmed, for these Journées. */
    private void signedUp(String personId, LocalDate... journees) {
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.of(eventRepository.findById(event),
                    userRepository.findById(UUID.fromString(personId)), fr.fruityhedgeh0g.enums.PosteEnum.CONDUCTEUR);
            for (LocalDate journee : journees) r.addJournee(journee, false);
            registrationRepository.persist(r);
        });
    }

    private List<LocalDate> journeesOf(String personId) {
        return QuarkusTransaction.requiringNew().call(() -> registrationRepository.findByEventAndPerson(event, UUID.fromString(personId))
                .map(EventRegistrationEntity::journeeDates).orElse(List.of()));
    }

    // --- Signing up per Journée ---

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void withoutJourneeOneSignsUpForTheWholeEvent() {
        signUpFor().statusCode(200)
                .body("journees.journee", contains(samedi.toString(), dimanche.toString()))
                .body("journees.status", contains("participant", "participant"));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void oneSignsUpForOneJourneeThenAddsAnother() {
        signUpFor(dimanche).statusCode(200).body("journees.journee", contains(dimanche.toString()));
        signUpFor(samedi).statusCode(200).body("journees.journee", contains(samedi.toString(), dimanche.toString()));
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void aDateOutsideTheEventIsNoJournee() {
        signUpFor(dimanche.plusDays(1)).statusCode(400);
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void addingAJourneeKeepsThePoste() {
        signUpFor(samedi).statusCode(200);
        given().queryParam("poste", "service").queryParam("journee", dimanche.toString())
                .when().put("/{id}/registration", event).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void oneWithdrawsFromOneJourneeThenFromTheLast() {
        signUpFor().statusCode(200);
        given().queryParam("journee", samedi.toString()).when().delete("/{id}/registration", event).then().statusCode(204);
        assertEquals(List.of(dimanche), journeesOf(ME));
        given().queryParam("journee", dimanche.toString()).when().delete("/{id}/registration", event).then().statusCode(204);
        assertTrue(journeesOf(ME).isEmpty());
    }

    // --- Maximums and Listes d'attente per Journée ---

    @Test
    @TestSecurity(user = "me", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void theMaximumCountsEachJourneeApart() {
        maximumIs(1);
        signedUp(OTHER_ID, samedi);

        signUpFor().statusCode(200)
                .body("journees.status", contains("en_attente", "participant"))
                .body("status", equalTo("participant"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theRosterShowsOneJourneeAtATimeAndCountsEach() {
        maximumIs(1);
        signedUp(OTHER_ID, samedi, dimanche);
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.of(eventRepository.findById(event),
                    userRepository.findById(UUID.fromString(ME)), fr.fruityhedgeh0g.enums.PosteEnum.SERVICE);
            r.addJournee(dimanche, true);
            registrationRepository.persist(r);
        });

        given().when().get("/{id}/roster", event).then().statusCode(200)
                .body("journee", equalTo(samedi.toString()))
                .body("journees.journee", contains(samedi.toString(), dimanche.toString()))
                .body("journees.participants", contains(1, 1))
                .body("journees.waiting", contains(0, 1))
                .body("waiting", org.hamcrest.Matchers.empty());
        given().queryParam("journee", dimanche.toString()).when().get("/{id}/roster", event).then().statusCode(200)
                .body("journee", equalTo(dimanche.toString()))
                .body("waiting.personId", contains(ME));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauMovesSomeoneUpForOneJournee() {
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.conducteur(eventRepository.findById(event),
                    userRepository.findById(UUID.fromString(ME)), true);
            registrationRepository.persist(r);
        });

        given().queryParam("journee", dimanche.toString()).when().post("/{e}/roster/{p}/promote", event, ME).then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity mine = registrationRepository.findByEventAndPerson(event, UUID.fromString(ME)).orElseThrow();
            assertTrue(mine.journee(samedi).orElseThrow().isWaiting());
            assertTrue(!mine.journee(dimanche).orElseThrow().isWaiting());
        });
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aDemandeIsAcceptedOnlyWithRoomInTheGroupeEveryJourneeOfThePerson() {
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).getGroupMaximums().put(nord, 1));
        signedUp(OTHER_ID, dimanche);
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.findByEventAndPerson(event, UUID.fromString(OTHER_ID))
                .orElseThrow().placeIn(groupRepository.findById(nord)));
        signedUp(ME, samedi, dimanche);
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.findByEventAndPerson(event, UUID.fromString(ME))
                .orElseThrow().requestGroup(groupRepository.findById(nord)));

        // Room on Saturday, none on Sunday
        given().when().post("/{e}/demandes/{p}/accept", event, ME).then().statusCode(400);
    }

    // --- The Event's dates ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void shorteningTheEventRemovesTheSignUpsOfItsDroppedJournees() {
        signedUp(ME, samedi, dimanche);
        signedUp(OTHER_ID, dimanche);

        given().contentType(ContentType.JSON)
                .body(Map.of("eventId", event.toString(), "endDateTime", samedi.atTime(18, 0).toString()))
                .when().patch().then().statusCode(200);

        assertEquals(List.of(samedi), journeesOf(ME));
        assertTrue(journeesOf(OTHER_ID).isEmpty(), "a sign-up left without Journée goes");
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theChefIsAtEveryJourneeIncludingOnesAddedLater() {
        given().basePath("/api").when().put("/groups/{g}/chef/{p}", nord, CHEF_ID).then().statusCode(200);
        assertEquals(List.of(samedi, dimanche), journeesOf(CHEF_ID));

        given().contentType(ContentType.JSON)
                .body(Map.of("eventId", event.toString(), "endDateTime", dimanche.plusDays(1).atTime(18, 0).toString()))
                .when().patch().then().statusCode(200);
        assertEquals(List.of(samedi, dimanche, dimanche.plusDays(1)), journeesOf(CHEF_ID));
    }
}
