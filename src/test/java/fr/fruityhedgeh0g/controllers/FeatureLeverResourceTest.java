package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** A Feature that is off is refused by the back end, for everyone, the Super admin included (ADR 0009). */
@QuarkusTest
class FeatureLeverResourceTest {

    static final String BENEVOLE = "00000000-0000-0000-0009-000000000001";
    static final String BUREAU = "00000000-0000-0000-0009-000000000002";
    static final String SUPER_ADMIN = "00000000-0000-0000-0009-000000000003";

    @Inject FeatureRepository featureRepository;
    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;
    @Inject GroupRepository groupRepository;
    @Inject EventRepository eventRepository;
    @Inject EventRegistrationRepository registrationRepository;

    private UUID sector;
    private UUID group;
    private UUID event;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.persist(person(BENEVOLE, RoleEnum.BENEVOLE));
            userRepository.persist(person(BUREAU, RoleEnum.BUREAU));
            userRepository.persist(person(SUPER_ADMIN, RoleEnum.SUPER_ADMIN));
            SectorEntity s = SectorEntity.builder().name("Lever Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            GroupEntity g = GroupEntity.builder().name("Lever Groupe " + UUID.randomUUID()).sector(s).build();
            groupRepository.persist(g);
            group = g.getGroupId();
            EventEntity e = new EventEntity();
            e.setName("Lever Event");
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
            for (FeatureEnum feature : FeatureEnum.values()) featureRepository.deleteById(feature.id());
            registrationRepository.delete("event.eventId", event);
            eventRepository.deleteById(event);
            groupRepository.deleteById(group);
            sectorRepository.deleteById(sector);
            List.of(BENEVOLE, BUREAU, SUPER_ADMIN).forEach(id -> userRepository.deleteById(UUID.fromString(id)));
        });
    }

    private static UserEntity person(String id, RoleEnum role) {
        return UserEntity.builder().userId(UUID.fromString(id)).firstName("Lever").lastName(role.name()).role(role).phone("06 00 00 00 00").build();
    }

    private void turn(FeatureEnum feature, boolean on) {
        QuarkusTransaction.requiringNew().run(() -> {
            FeatureEntity existing = featureRepository.findById(feature.id());
            if (existing == null) featureRepository.persist(FeatureEntity.builder().name(feature.id()).description("Test").isActive(on).build());
            else existing.setIsActive(on);
        });
    }

    private ValidatableResponse signUp() {
        return given().when().put("/api/events/{id}/registration", event).then();
    }

    private static void refusedAs(ValidatableResponse response, FeatureEnum feature) {
        response.statusCode(503).body("error", equalTo("feature-off")).body("feature", equalTo(feature.id()));
    }

    // --- Inscription aux événements ---

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE))
    void aFeatureNeverPulledIsOn() {
        signUp().statusCode(200);
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE))
    void inscriptionOffRefusesNewSignUpsAndDemandesButNotWithdrawing() {
        signUp().statusCode(200);
        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, false);

        refusedAs(given().contentType(ContentType.JSON).body("{\"groupId\":\"" + group + "\"}")
                .when().put("/api/events/{id}/registration/demande", event).then(), FeatureEnum.INSCRIPTION_EVENEMENTS);
        given().when().delete("/api/events/{id}/registration", event).then().statusCode(204);
        refusedAs(signUp(), FeatureEnum.INSCRIPTION_EVENEMENTS);

        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, true);
        signUp().statusCode(200);
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void theSuperAdminIsRefusedToo() {
        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, false);
        refusedAs(signUp(), FeatureEnum.INSCRIPTION_EVENEMENTS);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauStillManagesThoseAlreadySignedUp() {
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.persistConfirmed(
                eventRepository.findById(event), userRepository.findById(UUID.fromString(BENEVOLE))));
        turn(FeatureEnum.INSCRIPTION_EVENEMENTS, false);

        given().when().put("/api/events/{id}/roster/{person}/group/{group}", event, BENEVOLE, group).then().statusCode(200);
        given().when().delete("/api/events/{id}/roster/{person}", event, BENEVOLE).then().statusCode(200);
    }

    // --- Galerie photos ---

    @Test
    void galerieOffRefusesThePublicGallery() {
        given().when().get("/api/medias/gallery").then().statusCode(200);
        turn(FeatureEnum.GALERIE_PHOTOS, false);
        refusedAs(given().when().get("/api/medias/gallery").then(), FeatureEnum.GALERIE_PHOTOS);
        // The medias themselves still serve the rest of the site (logo, carousel, Posts)
        given().when().get("/api/medias").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauStillSeesTheGalleryItPrepares() {
        turn(FeatureEnum.GALERIE_PHOTOS, false);
        given().when().get("/api/medias/gallery").then().statusCode(200);
    }
}
