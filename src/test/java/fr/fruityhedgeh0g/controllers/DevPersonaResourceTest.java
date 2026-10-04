package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DevPersonaAuthentication;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Development personas: try the site as any Role, without Keycloak (dev and test builds only). */
@QuarkusTest
class DevPersonaResourceTest {

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;

    private UUID sector;

    @BeforeEach
    void seed() {
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Persona Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.delete("firstName", "Persona");
            userRepository.flush();
            sectorRepository.deleteById(sector);
        });
    }

    private static Response become(String body) {
        return given().contentType(ContentType.JSON).body(body).when().put("/api/dev/persona");
    }

    /** The persona cookie, as the browser keeps it. */
    private static String personaOf(Response response) {
        return response.then().statusCode(200).extract().cookie(DevPersonaAuthentication.COOKIE);
    }

    @Test
    void aVisiteurTakesAPersonaWithoutLoggingIn() {
        String bureau = personaOf(become("{\"role\":\"bureau\",\"sectorId\":\"" + sector + "\"}"));

        given().cookie(DevPersonaAuthentication.COOKIE, bureau).when().get("/api/users/me").then().statusCode(200)
                .body("role", equalTo("bureau"))
                .body("sector.sectorId", equalTo(sector.toString()));
        // What the Role may do can really be done
        given().cookie(DevPersonaAuthentication.COOKIE, bureau).when().get("/api/users").then().statusCode(200);
        given().cookie(DevPersonaAuthentication.COOKIE, bureau).when().get("/api/dev/persona").then().statusCode(200)
                .body("role", equalTo("bureau"));
    }

    @Test
    void aPersonaHasOnlyItsRolesRights() {
        String benevole = personaOf(become("{\"role\":\"benevole\"}"));
        given().cookie(DevPersonaAuthentication.COOKIE, benevole).when().get("/api/users").then().statusCode(403);
        given().cookie(DevPersonaAuthentication.COOKIE, benevole).when().get("/api/users/me").then()
                .body("role", equalTo("benevole")).body("sector", nullValue());
    }

    @Test
    void theSameRoleAndSecteurIsTheSamePerson() {
        String first = personaOf(become("{\"role\":\"admin\",\"sectorId\":\"" + sector + "\"}"));
        String again = personaOf(become("{\"role\":\"admin\",\"sectorId\":\"" + sector + "\"}"));
        assertEquals(first, again);
    }

    @Test
    void leavingThePersonaMakesAVisiteurAgain() {
        given().when().delete("/api/dev/persona").then().statusCode(204)
                .cookie(DevPersonaAuthentication.COOKIE, "");
        given().when().get("/api/dev/persona").then().statusCode(204);
        given().when().get("/api/users/me").then().statusCode(401);
    }

    @Test
    void aVisiteurIsNotAPersona() {
        become("{\"role\":\"visiteur\"}").then().statusCode(400);
    }
}
