package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.CarouselItemRepository;
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

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

/** The home page's carousel: Visiteurs see its active slides, the Bureau manages them all. */
@QuarkusTest
@TestHTTPEndpoint(CarouselController.class)
class CarouselResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0012-000000000005";

    @Inject CarouselItemRepository itemRepository;
    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;

    private UUID sector;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            userRepository.persist(UserEntity.builder().userId(UUID.fromString(BUREAU_ID)).firstName("Test").lastName("Bureau")
                    .role(RoleEnum.BUREAU).sector(s).build());
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            itemRepository.deleteAll();
            userRepository.deleteById(UUID.fromString(BUREAU_ID));
            sectorRepository.deleteById(sector);
        });
    }

    private ValidatableResponse create(String title, boolean active, String linkTo) {
        String link = linkTo == null ? "null" : "\"" + linkTo + "\"";
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"caption\":\"\",\"linkTo\":" + link + ",\"active\":" + active + "}")
                .when().post().then();
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauWritesAndOrdersSlides() {
        create("Balade", true, "/evenements").statusCode(200).body("order", equalTo(1));
        String loto = create("Loto", true, null).statusCode(200).body("order", equalTo(2)).extract().path("id");

        given().when().post("/" + loto + "/move/up").then().statusCode(200).body("title", contains("Loto", "Balade"));
        given().contentType(ContentType.JSON).body("{\"title\":\"Grand loto\",\"active\":false}")
                .when().put("/" + loto).then().statusCode(200).body("active", equalTo(false));
        given().when().get().then().body("title", contains("Grand loto", "Balade"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aSlideLinksToAPageOfTheSite() {
        create("Ailleurs", true, "https://example.org").statusCode(400);
        create("Ailleurs", true, "//example.org").statusCode(400);
        create(" ", true, null).statusCode(400);
    }

    @Test
    void visiteursSeeOnlyTheActiveSlides() {
        List.of(true, false).forEach(active -> QuarkusTransaction.requiringNew().run(() -> {
            var item = new fr.fruityhedgeh0g.entities.CarouselItemEntity();
            item.setTitle(active ? "Visible" : "De côté");
            item.setActive(active);
            item.setPosition(active ? 1 : 2);
            itemRepository.persist(item);
        }));
        given().when().get().then().statusCode(200).body("title", contains("Visible"));
    }

    @Test
    void anonymousVisiteurCannotWrite() {
        create("Balade", true, null).statusCode(401);
        given().when().get().then().body("$", empty());
    }

    @Test
    @TestSecurity(user = "membre", roles = {"benevole", "membre"})
    void aMembreCannotWrite() {
        create("Balade", true, null).statusCode(403);
    }
}
