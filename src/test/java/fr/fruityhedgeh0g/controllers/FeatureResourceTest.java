package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakRegistration;
import fr.fruityhedgeh0g.repositories.FeatureRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fonctionnalités: read by everyone, switched by the Super admin only (#27). */
@QuarkusTest
@TestHTTPEndpoint(FeatureController.class)
class FeatureResourceTest {

    static final String NAME = "test-feature";

    @Inject FeatureRepository featureRepository;
    @Inject FakeKeycloakRegistration keycloakRegistration;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() ->
                featureRepository.persist(FeatureEntity.builder().name(NAME).description("Test").isActive(false).build()));
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            featureRepository.delete("name", NAME);
            featureRepository.delete("name", FeatureEnum.INSCRIPTION_SITE.id());
        });
        keycloakRegistration.reset();
    }

    private ValidatableResponse turnOn(String name) {
        return given().contentType(ContentType.JSON).body("{\"isActive\":true}").when().put("/" + name).then();
    }

    @Test
    void anonymousVisiteurReadsThem() {
        given().when().get().then().statusCode(200).body("name", hasItem(NAME));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin"})
    void anAdminCannotSwitchThem() {
        turnOn(NAME).statusCode(403);
    }

    @Test
    @TestSecurity(user = "superadmin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin", "super_admin"})
    void theSuperAdminSwitchesThem() {
        turnOn(NAME).statusCode(200).body("isActive", equalTo(true));
        turnOn("test-unknown").statusCode(404);
    }

    private void seedInscriptionSurLeSite() {
        QuarkusTransaction.requiringNew().run(() -> featureRepository.persist(
                FeatureEntity.builder().name(FeatureEnum.INSCRIPTION_SITE.id()).description("Test").isActive(true).build()));
    }

    private ValidatableResponse turn(String name, boolean on) {
        return given().contentType(ContentType.JSON).body("{\"isActive\":" + on + "}").when().put("/" + name).then();
    }

    @Test
    @TestSecurity(user = "superadmin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin", "super_admin"})
    void inscriptionSurLeSiteClosesAndReopensKeycloaksForm() {
        seedInscriptionSurLeSite();
        turn(FeatureEnum.INSCRIPTION_SITE.id(), false).statusCode(200).body("isActive", equalTo(false));
        assertFalse(keycloakRegistration.allowed());
        turn(FeatureEnum.INSCRIPTION_SITE.id(), true).statusCode(200);
        assertTrue(keycloakRegistration.allowed());
    }

    @Test
    @TestSecurity(user = "superadmin", roles = {"benevole", "membre", "chef_de_groupe", "bureau", "admin", "super_admin"})
    void whenKeycloakFailsTheLeverStaysAsItWas() {
        seedInscriptionSurLeSite();
        keycloakRegistration.failing(true);
        turn(FeatureEnum.INSCRIPTION_SITE.id(), false).statusCode(502);
        assertTrue(QuarkusTransaction.requiringNew().call(() -> featureRepository.findById(FeatureEnum.INSCRIPTION_SITE.id()).getIsActive()));
    }
}
