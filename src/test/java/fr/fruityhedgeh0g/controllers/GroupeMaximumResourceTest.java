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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/** A Groupe's maximum at an Event, and the Groupe's Liste d'attente of pending Demandes. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class GroupeMaximumResourceTest {

    static final String ANNE_ID = "00000000-0000-0000-0008-000000000001";
    static final String BRUNO_ID = "00000000-0000-0000-0008-000000000002";
    static final String CLAIRE_ID = "00000000-0000-0000-0008-000000000003";
    static final String CHEF_ID = "00000000-0000-0000-0008-000000000004";
    static final String BUREAU_ID = "00000000-0000-0000-0008-000000000005";

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
    private final List<UUID> sectors = new ArrayList<>();
    private final Map<String, LocalDateTime> signUps = new HashMap<>();
    private UUID nord;
    private UUID elsewhere;
    private UUID event;

    @BeforeEach
    void seed() {
        persistPerson(ANNE_ID, RoleEnum.BENEVOLE, "Anne");
        persistPerson(BRUNO_ID, RoleEnum.BENEVOLE, "Bruno");
        persistPerson(CLAIRE_ID, RoleEnum.BENEVOLE, "Claire");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = persistSector();
            nord = persistGroup("Test Nord " + UUID.randomUUID(), s, CHEF_ID);
            elsewhere = persistGroup("Test Ailleurs " + UUID.randomUUID(), persistSector(), null);

            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
            e.setSector(s);
            eventRepository.persist(e);
            event = e.getEventId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sectors.get(0));
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sectors.get(0));
        QuarkusTransaction.requiringNew().run(() -> sectors.forEach(sector -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            groupRepository.delete("sector.sectorId", sector);
            sectorRepository.deleteById(sector);
        }));
        QuarkusTransaction.requiringNew().run(() -> persons.forEach(userRepository::deleteById));
        persons.clear();
        sectors.clear();
    }

    private SectorEntity persistSector() {
        SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
        sectorRepository.persist(s);
        sectors.add(s.getSectorId());
        return s;
    }

    private UUID persistGroup(String name, SectorEntity s, String chefId) {
        GroupEntity group = GroupEntity.builder().name(name).sector(s)
                .chef(chefId == null ? null : userRepository.findById(UUID.fromString(chefId))).build();
        groupRepository.persist(group);
        return group.getGroupId();
    }

    private void persistPerson(String id, RoleEnum role, String lastName) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(userId).firstName("Test").lastName(lastName).role(role).phone("06 00 00 00 00").build()
        ));
        persons.add(userId);
    }

    /** Signs the person up as a Participant asking for Nord, one minute after the previous sign-up. */
    private void asksForNord(String personId) {
        LocalDateTime at = LocalDateTime.now(EventEntity.ZONE).minusHours(1).plusMinutes(signUps.size());
        signUps.put(personId, at);
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.conducteur(
                    eventRepository.findById(event), userRepository.findById(UUID.fromString(personId)), false);
            r.setSignedUpAt(at);
            r.requestGroup(groupRepository.findById(nord));
            registrationRepository.persist(r);
        });
    }

    /** Asks for Nord and is accepted straight away. */
    private void ridesWithNord(String personId) {
        asksForNord(personId);
        QuarkusTransaction.requiringNew().run(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(personId)).orElseThrow().acceptDemande());
    }

    private void nordMaximumIs(Integer maximum) {
        QuarkusTransaction.requiringNew().run(() -> eventRepository.findById(event).getGroupMaximums().put(nord, maximum));
    }

    private ValidatableResponse setMaximum(UUID groupId, Integer maximum) {
        Map<String, Object> body = new HashMap<>();
        body.put("maximum", maximum);
        return given().contentType(ContentType.JSON).body(body)
                .when().put("/{e}/groups/{g}/maximum", event, groupId).then();
    }

    private ValidatableResponse accept(String personId) {
        return given().when().post("/{e}/demandes/{p}/accept", event, personId).then();
    }

    private String nordAt(String path) {
        return "groups.find { it.group.groupId == '" + nord + "' }." + path;
    }

    private String eventAt(String path) {
        return "events.find { it.eventId == '" + event + "' }." + path;
    }

    // --- Setting maximums ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSetsAGroupesMaximum() {
        setMaximum(nord, 2).statusCode(200).body(nordAt("maximum"), equalTo(2));
        given().when().get("/{e}/roster", event).then().statusCode(200).body(nordAt("maximum"), equalTo(2));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void zeroOrNothingRemovesTheMaximum() {
        setMaximum(nord, 2).statusCode(200);
        setMaximum(nord, 0).statusCode(200).body(nordAt("maximum"), nullValue());
        setMaximum(nord, 2).statusCode(200);
        setMaximum(nord, null).statusCode(200).body(nordAt("maximum"), nullValue());
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aGroupeOfAnotherSecteurHasNoMaximumAtThisEvent() {
        setMaximum(elsewhere, 2).statusCode(400);
        setMaximum(UUID.randomUUID(), 2).statusCode(404);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void aChefCannotSetAMaximum() {
        setMaximum(nord, 5).statusCode(403);
    }

    // --- Accepting within the maximum ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void acceptingBeyondTheGroupesMaximumIsRefused() {
        nordMaximumIs(1);
        ridesWithNord(ANNE_ID);
        asksForNord(BRUNO_ID);

        accept(BRUNO_ID).statusCode(400);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body(eventAt("members.lastName"), contains("Anne"))
                .body(eventAt("demandes.lastName"), contains("Bruno"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauCannotPlaceBeyondTheMaximumEither() {
        nordMaximumIs(1);
        ridesWithNord(ANNE_ID);
        asksForNord(BRUNO_ID);

        accept(BRUNO_ID).statusCode(400);
        given().when().put("/{e}/roster/{p}/group/{g}", event, BRUNO_ID, nord).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void withoutAMaximumEveryDemandeCanBeAccepted() {
        ridesWithNord(ANNE_ID);
        asksForNord(BRUNO_ID);
        accept(BRUNO_ID).statusCode(200);
    }

    // --- The Groupe's Liste d'attente ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void monGroupeShowsTheMaximumAndPendingDemandesInSignUpOrder() {
        nordMaximumIs(1);
        asksForNord(CLAIRE_ID);
        asksForNord(ANNE_ID);
        asksForNord(BRUNO_ID);

        given().when().get("/mon-groupe").then().statusCode(200)
                .body(eventAt("maximum"), equalTo(1))
                .body(eventAt("demandes.lastName"), contains("Claire", "Anne", "Bruno"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauRosterShowsEachGroupesListeDattenteInSignUpOrder() {
        nordMaximumIs(1);
        asksForNord(CLAIRE_ID);
        ridesWithNord(ANNE_ID);
        asksForNord(BRUNO_ID);

        given().when().get("/{e}/roster", event).then().statusCode(200)
                .body(nordAt("maximum"), equalTo(1))
                .body(nordAt("members.lastName"), contains("Anne"))
                .body(nordAt("demandes.lastName"), contains("Claire", "Bruno"));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void takingSomeoneOutFreesAPlaceWithoutAcceptingAnyone() {
        nordMaximumIs(1);
        ridesWithNord(ANNE_ID);
        asksForNord(BRUNO_ID);
        asksForNord(CLAIRE_ID);

        given().when().delete("/{e}/roster/{p}/group", event, ANNE_ID).then().statusCode(200);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body(eventAt("members"), empty())
                .body(eventAt("demandes.lastName"), contains("Bruno", "Claire"));

        accept(CLAIRE_ID).statusCode(200);
        accept(BRUNO_ID).statusCode(400);
    }
}
