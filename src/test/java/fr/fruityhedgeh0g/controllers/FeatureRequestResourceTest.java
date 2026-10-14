package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.FeatureRequestRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/** Feature requests: written and read by the Bureau and above. */
@QuarkusTest
@TestHTTPEndpoint(FeatureRequestController.class)
class FeatureRequestResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0041-000000000001";
    static final String MEMBRE_ID = "00000000-0000-0000-0041-000000000002";

    @Inject UserRepository userRepository;
    @Inject FeatureRequestRepository requestRepository;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.persist(UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(UUID.fromString(BUREAU_ID)).firstName("Claire").lastName("Hoffmann").role(RoleEnum.BUREAU).build());
            userRepository.persist(UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(UUID.fromString(MEMBRE_ID)).firstName("Test").lastName("Membre").role(RoleEnum.MEMBRE).build());
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            requestRepository.deleteAll();
            userRepository.deleteById(UUID.fromString(BUREAU_ID));
            userRepository.deleteById(UUID.fromString(MEMBRE_ID));
        });
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauAsksForAFeature() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Export\",\"description\":\"Un export des dons\"}")
                .when().post().then().statusCode(200).body("requestedBy", equalTo("Claire Hoffmann"));
        given().when().get().then().statusCode(200).body("title", contains("Export"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aRequestHasATitleAndADescription() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Export\",\"description\":\" \"}").when().post().then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void belowTheBureauNobodyAsks() {
        given().when().get().then().statusCode(403);
    }
}
