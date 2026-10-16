package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.repositories.CarouselItemRepository;
import fr.fruityhedgeh0g.repositories.MediaFileRepository;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.builder.MultiPartSpecBuilder;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Medias: listed and served to everyone, uploaded by the Bureau, kept in the database (ADR 0008). */
@QuarkusTest
@TestHTTPEndpoint(MediaController.class)
public class MediaResourceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    @Inject MediaRepository mediaRepository;
    @Inject MediaFileRepository fileRepository;
    @Inject CarouselItemRepository carouselRepository;

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            carouselRepository.deleteAll();
            fileRepository.deleteAll();
            mediaRepository.deleteAll();
        });
    }

    private String upload(String fileName, String type, byte[] content) {
        // A browser sends the text fields of a form in UTF-8
        var alt = new MultiPartSpecBuilder("Le départ de la balade").controlName("alt").charset(StandardCharsets.UTF_8).build();
        return given().multiPart("file", fileName, content, type).multiPart(alt)
                .when().post().then().statusCode(200)
                .body("mimeType", equalTo(type))
                .body("alt", equalTo("Le départ de la balade"))
                .extract().path("mediaId");
    }

    @Test
    @TestSecurity(user = "bureau", roles = {"benevole", "membre", "chef_de_groupe", "bureau"})
    void theBureauUploadsAnImageThatEveryoneSees() {
        String mediaId = upload("depart.png", "image/png", PNG);

        given().when().get().then().statusCode(200).body("mediaId", hasItem(mediaId));
        byte[] served = given().when().get("/" + mediaId + "/content").then().statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", containsString("immutable"))
                .extract().asByteArray();
        assertArrayEquals(PNG, served);
    }

    @Test
    @TestSecurity(user = "bureau", roles = {"benevole", "membre", "chef_de_groupe", "bureau"})
    void theBureauDescribesAnImage() {
        String mediaId = upload("depart.png", "image/png", PNG);
        given().contentType("application/json").body("{\"alt\":\"L'arrivée\"}")
                .when().patch("/" + mediaId).then().statusCode(200).body("alt", equalTo("L'arrivée"));
    }

    @Test
    @TestSecurity(user = "bureau", roles = {"benevole", "membre", "chef_de_groupe", "bureau"})
    void onlyImagesAreAccepted() {
        given().multiPart("file", "logo.svg", "<svg/>".getBytes(), "image/svg+xml").when().post().then().statusCode(400);
        given().multiPart("file", "film.mp4", PNG, "video/mp4").when().post().then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", roles = {"benevole", "membre", "chef_de_groupe", "bureau"})
    void anImageWeighsAtMostEightMegabytes() {
        byte[] heavy = Arrays.copyOf(PNG, 8 * 1024 * 1024 + 1);
        given().multiPart("file", "lourd.png", heavy, "image/png").when().post().then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "membre", roles = {"benevole", "membre"})
    void aMembreCannotUpload() {
        given().multiPart("file", "depart.png", PNG, "image/png").when().post().then().statusCode(403);
    }

    @Test
    void anonymousVisiteurCannotUpload() {
        given().multiPart("file", "depart.png", PNG, "image/png").when().post().then().statusCode(401);
    }

    @Test
    void anUnknownMediaHasNoContent() {
        given().when().get("/00000000-0000-0000-0000-000000000000/content").then().statusCode(404);
    }
}
