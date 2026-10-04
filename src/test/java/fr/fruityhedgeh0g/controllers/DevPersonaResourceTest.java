package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
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

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Development personas: the logged-in person takes any Role to try the site, without another account. */
@QuarkusTest
class DevPersonaResourceTest {

    static final String ME = "00000000-0000-0000-0099-000000000001";

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;

    private UUID sector;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Persona Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            userRepository.persist(UserEntity.builder().userId(UUID.fromString(ME)).firstName("QA").lastName("Persona")
                    .role(RoleEnum.BENEVOLE).build());
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.deleteById(UUID.fromString(ME));
            userRepository.flush();
            sectorRepository.deleteById(sector);
        });
    }

    private static ValidatableResponse become(String body) {
        return given().contentType(ContentType.JSON).body(body).when().put("/api/dev/persona").then();
    }

    @Test
    @TestSecurity(user = "qa", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void aPersonTakesAnyRoleWithASecteurWhenItHasOne() {
        become("{\"role\":\"bureau\",\"sectorId\":\"" + sector + "\"}").statusCode(200)
                .body("role", equalTo("bureau")).body("sectorId", equalTo(sector.toString()));
        given().when().get("/api/users/me").then().statusCode(200).body("role", equalTo("bureau"));

        become("{\"role\":\"super_admin\"}").statusCode(200).body("sectorId", nullValue());
        become("{\"role\":\"admin\"}").statusCode(200).body("sectorId", notNullValue());
        become("{\"role\":\"benevole\"}").statusCode(200);
        assertEquals(RoleEnum.BENEVOLE, QuarkusTransaction.requiringNew().call(() -> userRepository.findById(UUID.fromString(ME)).getRole()));
    }

    @Test
    @TestSecurity(user = "qa", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void aVisiteurIsNotAPersona() {
        become("{\"role\":\"visiteur\"}").statusCode(400);
    }

    @Test
    @TestSecurity(user = "qa", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ME))
    void theSiteAsksWhetherPersonasAreOffered() {
        given().when().get("/api/dev/persona").then().statusCode(204);
    }

    @Test
    void itNeedsSomeoneLoggedIn() {
        become("{\"role\":\"bureau\"}").statusCode(401);
    }
}
