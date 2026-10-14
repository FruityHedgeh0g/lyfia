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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/** Demande de groupe at sign-up, decided by the Groupe's Chef or the Bureau, and Mon groupe. */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class DemandeResourceTest {

    static final String CONDUCTEUR_ID = "00000000-0000-0000-0007-000000000002";
    static final String CHEF_ID = "00000000-0000-0000-0007-000000000004";
    static final String OTHER_CHEF_ID = "00000000-0000-0000-0007-000000000014";
    static final String IDLE_CHEF_ID = "00000000-0000-0000-0007-000000000024";
    static final String BUREAU_ID = "00000000-0000-0000-0007-000000000005";

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
    private UUID sud;
    private UUID event;

    @BeforeEach
    void seed() {
        persistPerson(UUID.fromString(CONDUCTEUR_ID), RoleEnum.BENEVOLE, "Conducteur");
        persistPerson(UUID.fromString(CHEF_ID), RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(UUID.fromString(OTHER_CHEF_ID), RoleEnum.CHEF_DE_GROUPE, "Autre");
        persistPerson(UUID.fromString(IDLE_CHEF_ID), RoleEnum.CHEF_DE_GROUPE, "Libre");
        persistPerson(UUID.fromString(BUREAU_ID), RoleEnum.BUREAU, "Bureau");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            nord = persistGroup("Test Nord " + UUID.randomUUID(), s, CHEF_ID);
            sud = persistGroup("Test Sud " + UUID.randomUUID(), s, OTHER_CHEF_ID);

            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
            e.setSector(s);
            eventRepository.persist(e);
            event = e.getEventId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.delete("sector.sectorId", sector);
            groupRepository.delete("sector.sectorId", sector);
            sectorRepository.deleteById(sector);
            persons.forEach(userRepository::deleteById);
        });
        persons.clear();
    }

    private UUID persistGroup(String name, SectorEntity s, String chefId) {
        GroupEntity group = GroupEntity.builder().name(name).sector(s).chef(userRepository.findById(UUID.fromString(chefId))).build();
        groupRepository.persist(group);
        return group.getGroupId();
    }

    private UUID persistPerson(UUID id, RoleEnum role, String lastName) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(id).firstName("Test").lastName(lastName).role(role).phone("06 00 00 00 00").build()
        ));
        persons.add(id);
        return id;
    }

    /** Signs the conducteur up directly in the database, optionally with a pending Demande. */
    private void conducteurSignedUp(UUID demandeGroup) {
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.conducteur(
                    eventRepository.findById(event), userRepository.findById(UUID.fromString(CONDUCTEUR_ID)), false);
            if (demandeGroup != null) r.requestGroup(groupRepository.findById(demandeGroup));
            registrationRepository.persist(r);
        });
    }

    private ValidatableResponse decide(String decision) {
        return given().when().post("/{e}/demandes/{p}/{d}", event, CONDUCTEUR_ID, decision).then();
    }

    private ValidatableResponse myRegistration() {
        return given().when().get("/registrations").then().statusCode(200);
    }

    // --- Demande at sign-up ---

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void signUpWithAGroupeCreatesAPendingDemande() {
        given().queryParam("groupId", nord.toString())
                .when().put("/{id}/registration", event).then().statusCode(200)
                .body("status", equalTo("participant"))
                .body("group", nullValue())
                .body("demande.group.groupId", equalTo(nord.toString()))
                .body("demande.status", equalTo("en_attente"));

        myRegistration().body("[0].demande.status", equalTo("en_attente"));
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void signUpWithoutAGroupeMakesNoDemande() {
        given().when().put("/{id}/registration", event).then().statusCode(200).body("demande", nullValue());
    }

    // --- Deciding ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void theGroupesChefAcceptsTheDemande() {
        conducteurSignedUp(nord);
        decide("accept").statusCode(200)
                .body("group.groupId", equalTo(nord.toString()))
                .body("demande.status", equalTo("acceptee"));
    }

    @Test
    @TestSecurity(user = "other-chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = OTHER_CHEF_ID))
    void aChefOfAnotherGroupeCannotDecide() {
        conducteurSignedUp(nord);
        decide("accept").statusCode(403);
        decide("refuse").statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauDecidesForAnyGroupe() {
        conducteurSignedUp(sud);
        decide("accept").statusCode(200).body("group.groupId", equalTo(sud.toString()));
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void aConducteurCannotDecideTheirOwnDemande() {
        conducteurSignedUp(nord);
        decide("accept").statusCode(403);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void refusalLeavesAParticipantWithoutAGroupe() {
        conducteurSignedUp(nord);
        decide("refuse").statusCode(200)
                .body("group", nullValue())
                .body("demande.status", equalTo("refusee"));
        decide("accept").statusCode(400);
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void afterARefusalANewDemandeCanBeMade() {
        conducteurSignedUp(nord);
        QuarkusTransaction.requiringNew().run(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(CONDUCTEUR_ID)).orElseThrow().refuseDemande());

        given().contentType(ContentType.JSON).body(Map.of("groupId", sud.toString()))
                .when().put("/{id}/registration/demande", event).then().statusCode(200)
                .body("demande.group.groupId", equalTo(sud.toString()))
                .body("demande.status", equalTo("en_attente"));
        myRegistration().body("[0].status", equalTo("participant"));
    }

    // --- Placing and taking out ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauPlacesAParticipantDirectly() {
        conducteurSignedUp(null);
        given().when().put("/{e}/roster/{p}/group/{g}", event, CONDUCTEUR_ID, nord).then().statusCode(200)
                .body("participants[0].group.groupId", equalTo(nord.toString()));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void aChefCannotPlaceDirectly() {
        conducteurSignedUp(null);
        given().when().put("/{e}/roster/{p}/group/{g}", event, CONDUCTEUR_ID, nord).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void theChefTakesAParticipantOutOfTheGroupe() {
        conducteurSignedUp(nord);
        decide("accept").statusCode(200);

        given().when().delete("/{e}/roster/{p}/group", event, CONDUCTEUR_ID).then().statusCode(200)
                .body("group", nullValue())
                .body("status", equalTo("participant"));
    }

    @Test
    @TestSecurity(user = "other-chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = OTHER_CHEF_ID))
    void aChefOfAnotherGroupeCannotTakeOut() {
        conducteurSignedUp(nord);
        QuarkusTransaction.requiringNew().run(() ->
                registrationRepository.findByEventAndPerson(event, UUID.fromString(CONDUCTEUR_ID)).orElseThrow().acceptDemande());
        given().when().delete("/{e}/roster/{p}/group", event, CONDUCTEUR_ID).then().statusCode(403);
    }

    // --- Mon groupe ---

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void monGroupeShowsParticipantsAndPendingDemandesPerEvent() {
        conducteurSignedUp(nord);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("group.groupId", equalTo(nord.toString()))
                .body("events.find { it.eventId == '" + event + "' }.members", empty())
                .body("events.find { it.eventId == '" + event + "' }.demandes.lastName", contains("Conducteur"));

        decide("accept").statusCode(200);
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("events.find { it.eventId == '" + event + "' }.members.lastName", contains("Conducteur"))
                .body("events.find { it.eventId == '" + event + "' }.demandes", empty());
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void monGroupeListsTheSecteursEventsEvenWithNobodyYet() {
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("events.find { it.eventId == '" + event + "' }.members", empty())
                .body("events.find { it.eventId == '" + event + "' }.demandes", empty());
    }

    @Test
    @TestSecurity(user = "idle-chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = IDLE_CHEF_ID))
    void monGroupeIsEmptyWithoutAnAffectation() {
        given().when().get("/mon-groupe").then().statusCode(200)
                .body("group", nullValue())
                .body("events", empty());
    }

    @Test
    @TestSecurity(user = "conducteur", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CONDUCTEUR_ID))
    void monGroupeNeedsChefDeGroupe() {
        given().when().get("/mon-groupe").then().statusCode(403);
    }
}
