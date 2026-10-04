package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.PostEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureSectorLeverEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.repositories.FeatureSectorLeverRepository;
import fr.fruityhedgeh0g.repositories.FeatureSwitchRepository;
import fr.fruityhedgeh0g.repositories.PostRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * A Feature about what belongs to a Secteur also has a lever per Secteur, independent of the site-wide one: it works
 * in a Secteur only while both are on (ADR 0009).
 */
@QuarkusTest
class SectorLeverResourceTest {

    static final String BENEVOLE = "00000000-0000-0000-0009-000000000201";
    static final String BUREAU = "00000000-0000-0000-0009-000000000202";
    static final String SUPER_ADMIN = "00000000-0000-0000-0009-000000000203";
    static final FeatureEnum INSCRIPTION = FeatureEnum.INSCRIPTION_EVENEMENTS;

    @Inject FeatureRepository featureRepository;
    @Inject FeatureSectorLeverRepository sectorLeverRepository;
    @Inject FeatureSwitchRepository switchRepository;
    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;
    @Inject EventRepository eventRepository;
    @Inject EventRegistrationRepository registrationRepository;
    @Inject PostRepository postRepository;

    private UUID algrange;
    private UUID thionville;
    private UUID closed;
    private UUID algrangeEvent;
    private UUID thionvilleEvent;
    private UUID algrangePost;
    private UUID thionvillePost;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (FeatureEnum feature : List.of(INSCRIPTION, FeatureEnum.EXPORT_LISTE, FeatureEnum.GALERIE_PHOTOS, FeatureEnum.ACTUALITES))
                featureRepository.persist(FeatureEntity.builder().name(feature.id()).description("Test").isActive(true).build());
            SectorEntity a = sector("Algrange", false);
            SectorEntity t = sector("Thionville", false);
            closed = sector("Fermé", true).getSectorId();
            algrange = a.getSectorId();
            thionville = t.getSectorId();
            userRepository.persist(person(BENEVOLE, RoleEnum.BENEVOLE, null));
            userRepository.persist(person(BUREAU, RoleEnum.BUREAU, a));
            userRepository.persist(person(SUPER_ADMIN, RoleEnum.SUPER_ADMIN, null));
            algrangeEvent = event(a);
            thionvilleEvent = event(t);
            algrangePost = post(a);
            thionvillePost = post(t);
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            switchRepository.deleteAll();
            postRepository.delete("postId in ?1", List.of(algrangePost, thionvillePost));
            sectorLeverRepository.deleteAll();
            featureRepository.delete("name in ?1", Arrays.stream(FeatureEnum.values()).map(FeatureEnum::id).toList());
            registrationRepository.delete("event.eventId in ?1", List.of(algrangeEvent, thionvilleEvent));
            eventRepository.delete("eventId in ?1", List.of(algrangeEvent, thionvilleEvent));
            userRepository.delete("userId in ?1", List.of(BENEVOLE, BUREAU, SUPER_ADMIN).stream().map(UUID::fromString).toList());
            sectorRepository.delete("sectorId in ?1", List.of(algrange, thionville, closed));
        });
    }

    private SectorEntity sector(String name, boolean isClosed) {
        SectorEntity s = SectorEntity.builder().name("Lever " + name + " " + UUID.randomUUID()).build();
        s.setClosed(isClosed);
        sectorRepository.persist(s);
        return s;
    }

    private static UserEntity person(String id, RoleEnum role, SectorEntity sector) {
        return UserEntity.builder().userId(UUID.fromString(id)).firstName("Lever").lastName(role.name())
                .role(role).phone("06 00 00 00 00").sector(sector).build();
    }

    private UUID event(SectorEntity sector) {
        EventEntity e = new EventEntity();
        e.setName("Lever Event");
        e.setStatus(EventStatusEnum.OUVERT);
        e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
        e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
        e.setSector(sector);
        eventRepository.persist(e);
        return e.getEventId();
    }

    private UUID post(SectorEntity sector) {
        PostEntity post = new PostEntity();
        post.setTitle("Lever actualité");
        post.setContent("Contenu");
        post.setStatus(PostStatusEnum.PUBLIE);
        post.setSector(sector);
        postRepository.persist(post);
        return post.getPostId();
    }

    private void turnOffIn(FeatureEnum feature, UUID sectorId) {
        QuarkusTransaction.requiringNew().run(() -> sectorLeverRepository.persist(FeatureSectorLeverEntity.builder()
                .feature(feature.id()).sector(sectorRepository.findById(sectorId)).isActive(false).build()));
    }

    private static ValidatableResponse switchIn(FeatureEnum feature, UUID sectorId, boolean on) {
        return given().contentType(ContentType.JSON).body("{\"isActive\":" + on + ",\"reason\":\"Test\"}")
                .when().put("/api/features/{name}/sectors/{sector}", feature.id(), sectorId).then();
    }

    private static ValidatableResponse switchSiteWide(FeatureEnum feature, boolean on) {
        return given().contentType(ContentType.JSON).body("{\"isActive\":" + on + "}")
                .when().put("/api/features/{name}", feature.id()).then();
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE))
    void offForOneSecteurStopsOnlyThatSecteur() {
        turnOffIn(INSCRIPTION, algrange);
        given().when().put("/api/events/{id}/registration", algrangeEvent).then()
                .statusCode(503).body("feature", equalTo(INSCRIPTION.id()));
        // Thionville has no lever of its own: a Secteur starts with every lever on
        given().when().put("/api/events/{id}/registration", thionvilleEvent).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void exportOffForItsSecteurRefusesItsSpreadsheet() {
        turnOffIn(FeatureEnum.EXPORT_LISTE, algrange);
        given().when().get("/api/events/{id}/roster/export", algrangeEvent).then()
                .statusCode(503).body("feature", equalTo(FeatureEnum.EXPORT_LISTE.id()));
        given().when().get("/api/events/{id}/roster", algrangeEvent).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void theSiteWideLeverBackOnLeavesASecteurLeverAsItWas() {
        switchIn(INSCRIPTION, algrange, false).statusCode(200).body("offSectors", contains(algrange.toString()));
        switchSiteWide(INSCRIPTION, false).statusCode(200);
        switchSiteWide(INSCRIPTION, true).statusCode(200).body("offSectors", contains(algrange.toString()));

        switchIn(INSCRIPTION, algrange, true).statusCode(200).body("offSectors", empty());
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void eachSecteurSwitchGoesToTheJournalWithItsSecteur() {
        switchIn(INSCRIPTION, algrange, false).statusCode(200);
        given().when().get("/api/features/journal").then().statusCode(200)
                .body("[0].sectorId", equalTo(algrange.toString()))
                .body("[0].sectorName", equalTo(QuarkusTransaction.requiringNew().call(() -> sectorRepository.findById(algrange).getName())))
                .body("[0].reason", equalTo("Test"))
                .body("[0].switchedBy", equalTo("Lever SUPER_ADMIN"));
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void onlyFeaturesAboutASecteurHaveSecteurLevers() {
        switchIn(FeatureEnum.GALERIE_PHOTOS, algrange, false).statusCode(400);
        switchIn(FeatureEnum.ACTUALITES, algrange, false).statusCode(200);
        switchIn(INSCRIPTION, closed, false).statusCode(400);
        switchIn(INSCRIPTION, UUID.randomUUID(), false).statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin"})
    void onlyTheSuperAdminSwitchesThem() {
        switchIn(INSCRIPTION, algrange, false).statusCode(403);
    }

    @Test
    void actualitesOffForOneSecteurKeepsOnlyItsPostsAwayFromThePublic() {
        turnOffIn(FeatureEnum.ACTUALITES, algrange);
        given().when().get("/api/posts").then().statusCode(200)
                .body("postId", hasItem(thionvillePost.toString()))
                .body("postId", not(hasItem(algrangePost.toString())));
        given().when().get("/api/posts/{id}", algrangePost).then()
                .statusCode(503).body("feature", equalTo(FeatureEnum.ACTUALITES.id()));
        given().when().get("/api/posts/{id}", thionvillePost).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauStillReadsThePostsItPrepares() {
        turnOffIn(FeatureEnum.ACTUALITES, algrange);
        given().when().get("/api/posts").then().statusCode(200).body("postId", hasItem(algrangePost.toString()));
        given().when().get("/api/posts/{id}", algrangePost).then().statusCode(200);
    }
}
