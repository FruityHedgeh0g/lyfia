package fr.fruityhedgeh0g.controllers;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestHTTPEndpoint(AuthController.class)
class AuthResourceTest {

    @Test
    @TestSecurity(user = "benevole")
    void loggedInGoesBackToThePageTheyCameFrom() {
        given().redirects().follow(false).queryParam("redirect", "/evenements/42?tab=roster")
                .when().get("/login")
                .then().statusCode(303).header("Location", matchesPattern("^https?://[^/]+/evenements/42\\?tab=roster$"));
    }

    @Test
    @TestSecurity(user = "benevole")
    void withoutAPageGoesHome() {
        given().redirects().follow(false).when().get("/login")
                .then().statusCode(303).header("Location", matchesPattern("^https?://[^/]+/$"));
    }

    @Test
    void anonymousMustLogInFirst() {
        given().redirects().follow(false).when().get("/login").then().statusCode(401);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, /",
            "'', /",
            "/, /",
            "/mon-espace, /mon-espace",
            "https://evil.example, /",
            "//evil.example, /",
            "/\\evil.example, /",
            "evil.example, /",
    })
    void sendsBackOnlyToThisSite(String redirect, String expected) {
        assertEquals(expected, AuthController.sitePath(redirect));
    }
}
