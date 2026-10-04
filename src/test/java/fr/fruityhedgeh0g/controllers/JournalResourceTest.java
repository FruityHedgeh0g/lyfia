package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import fr.fruityhedgeh0g.repositories.FeatureSwitchRepository;
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

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/** The Journal: every switch of a Feature, who made it, when and why, and what the Feature refused (ADR 0009). */
@QuarkusTest
class JournalResourceTest {

    static final String SUPER_ADMIN = "00000000-0000-0000-0009-000000000101";
    static final String FEATURE = FeatureEnum.GALERIE_PHOTOS.id();

    @Inject FeatureRepository featureRepository;
    @Inject FeatureSwitchRepository switchRepository;
    @Inject UserRepository userRepository;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.persist(UserEntity.builder().userId(UUID.fromString(SUPER_ADMIN))
                    .firstName("Sam").lastName("Levier").role(RoleEnum.SUPER_ADMIN).build());
            featureRepository.persist(FeatureEntity.builder().name(FEATURE).description("Test").isActive(true).build());
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            switchRepository.deleteAll();
            featureRepository.deleteById(FEATURE);
            userRepository.deleteById(UUID.fromString(SUPER_ADMIN));
        });
    }

    private static ValidatableResponse turn(boolean on, String reason) {
        String body = reason == null ? "{\"isActive\":" + on + "}" : "{\"isActive\":" + on + ",\"reason\":\"" + reason + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().put("/api/features/" + FEATURE).then();
    }

    private static ValidatableResponse journal(int page, int size) {
        return given().queryParam("page", page).queryParam("size", size).when().get("/api/features/journal").then();
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void everySwitchIsWrittenWithWhoWhenAndWhy() {
        turn(false, "Spam dans la galerie").statusCode(200)
                .body("lastSwitchedBy", equalTo("Sam Levier"))
                .body("lastReason", equalTo("Spam dans la galerie"))
                .body("lastSwitchedAt", notNullValue());
        turn(true, null).statusCode(200);

        journal(0, 20).statusCode(200)
                .body("", hasSize(2))
                .body("[0].feature", equalTo(FEATURE))
                .body("[0].isActive", equalTo(true))
                .body("[0].sectorId", equalTo(null))
                .body("[1].isActive", equalTo(false))
                .body("[1].reason", equalTo("Spam dans la galerie"))
                .body("[1].switchedBy", equalTo("Sam Levier"));
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void theJournalComesOnePageAtATime() {
        turn(false, null);
        turn(true, null);
        turn(false, null);
        journal(0, 2).statusCode(200).body("", hasSize(2));
        journal(1, 2).statusCode(200).body("", hasSize(1));
        journal(-1, 2).statusCode(400);
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void refusalsAreCountedSinceTheFeatureWasTurnedOff() {
        turn(false, null);
        QuarkusTransaction.requiringNew().run(() ->
                featureRepository.update("refusedCount = 3 where name = ?1", FEATURE));
        given().when().get("/api/features").then().statusCode(200)
                .body("find { it.name == '" + FEATURE + "' }.refusedCount", equalTo(3));

        turn(true, null);
        turn(false, null);
        given().when().get("/api/features").then()
                .body("find { it.name == '" + FEATURE + "' }.refusedCount", equalTo(0));
    }

    @Test
    void eachRefusalIsCounted() {
        QuarkusTransaction.requiringNew().run(() -> featureRepository.findById(FEATURE).setIsActive(false));
        given().when().get("/api/medias/gallery").then().statusCode(503);
        given().when().get("/api/medias/gallery").then().statusCode(503);
        given().when().get("/api/features").then()
                .body("find { it.name == '" + FEATURE + "' }.refusedCount", equalTo(2));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin"})
    void onlyTheSuperAdminReadsTheJournal() {
        journal(0, 20).statusCode(403);
    }

    @Test
    @TestSecurity(user = "superadmin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin", "super_admin"})
    void theJournalIsNeverChangedNorDeletedFromTheSite() {
        given().when().delete("/api/features/journal").then().statusCode(405);
        given().contentType(ContentType.JSON).body("{}").when().post("/api/features/journal").then().statusCode(405);
    }
}
