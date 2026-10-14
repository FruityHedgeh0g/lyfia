package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestHTTPEndpoint(UserController.class)
public class UserResourceTest {

    static final String BENEVOLE_ID = "00000000-0000-0000-0000-000000000002";
    static final String MEMBRE_ID = "00000000-0000-0000-0000-000000000003";
    static final String BUREAU_ID = "00000000-0000-0000-0000-000000000005";
    static final String ADMIN_ID = "00000000-0000-0000-0000-000000000006";
    static final String UNKNOWN_ID = "00000000-0000-0000-0000-0000000000ff";
    /** Registered in Keycloak before the application was deployed: no row until their first visit. */
    static final String NEWCOMER_ID = "00000000-0000-0000-0000-0000000000fe";

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void persons() {
        QuarkusTransaction.requiringNew().run(() -> {
            person(BENEVOLE_ID, RoleEnum.BENEVOLE);
            person(MEMBRE_ID, RoleEnum.MEMBRE);
            person(BUREAU_ID, RoleEnum.BUREAU);
            person(ADMIN_ID, RoleEnum.ADMIN);
        });
    }

    @AfterEach
    void removePersons() {
        QuarkusTransaction.requiringNew().run(() ->
                Stream.of(BENEVOLE_ID, MEMBRE_ID, BUREAU_ID, ADMIN_ID, NEWCOMER_ID).map(UUID::fromString).forEach(userRepository::deleteById)
        );
    }

    private void person(String id, RoleEnum role) {
        userRepository.persist(UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(UUID.fromString(id)).firstName("Test").lastName(role.name()).role(role).build());
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE_ID))
    void currentPersonSeesTheirRole() {
        given().when().get("/me").then().statusCode(200).body("role", equalTo("benevole"));
    }

    /** Who rides with a Groupe lives on each Event's sign-ups: a person has no Groupe of their own. */
    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE_ID))
    void aPersonHasNoGroupeOfTheirOwn() {
        given().when().get("/me").then().statusCode(200).body("$", not(hasKey("group")));
    }

    @Test
    void anonymousVisiteurCannotListPersons() {
        given().when().get("/").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "newcomer", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = NEWCOMER_ID),
            @Claim(key = "given_name", value = "Camille"),
            @Claim(key = "family_name", value = "Martin")
    })
    void aPersonKeycloakNeverAnnouncedJoinsAsBenevoleOnTheirFirstVisit() {
        given().when().get("/me").then().statusCode(200)
                .body("firstName", equalTo("Camille"))
                .body("lastName", equalTo("Martin"))
                .body("role", equalTo("benevole"));
        // Kept: the next visit finds the same person
        given().when().get("/me").then().statusCode(200).body("firstName", equalTo("Camille"));
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = {@Claim(key = "sub", value = BENEVOLE_ID), @Claim(key = "given_name", value = "Other")})
    void aKnownPersonKeepsTheirNamesWhateverTheToken() {
        given().when().get("/me").then().statusCode(200).body("firstName", equalTo("Test"));
    }

    @Test
    void anonymousVisiteurHasNoCurrentPerson() {
        given().when().get("/me").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "unknown", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = UNKNOWN_ID))
    void personUnknownToTheDatabaseCannotListPersons() {
        given().when().get("/").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void membreCannotListPersons() {
        given().when().get("/").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void bureauListsPersons() {
        given().when().get("/").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void adminListsPersons() {
        given().when().get("/").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "membre", roles = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void tokenRolesDoNotOverrideTheDatabaseRole() {
        given().when().get("/").then().statusCode(403);
    }
}
