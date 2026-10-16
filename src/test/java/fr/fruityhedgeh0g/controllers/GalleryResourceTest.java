package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.configurations.ConfigurationEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureSectorLeverEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.ConfigurationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.FeatureSectorLeverRepository;
import fr.fruityhedgeh0g.repositories.GalleryPhotoRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
import fr.fruityhedgeh0g.repositories.MediaFileRepository;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Galerie (ADR 0013, ADR 0014): the Bureau deposits a Secteur's photos with the variants its browser made, as
 * Brouillons; each photo's Visibilité decides who sees it and its files.
 */
@QuarkusTest
public class GalleryResourceTest {

    static final String BUREAU = "00000000-0000-0000-0017-000000000001";
    static final String OTHER_BUREAU = "00000000-0000-0000-0017-000000000002";
    static final String BENEVOLE = "00000000-0000-0000-0017-000000000003";
    static final String SUPER_ADMIN = "00000000-0000-0000-0017-000000000004";
    static final byte[] ORIGINAL = {(byte) 0xff, (byte) 0xd8, 'o', 'r', 'i', 'g', 'i', 'n', 'a', 'l'};
    static final byte[] AFFICHAGE = {(byte) 0xff, (byte) 0xd8, 'a', 'f', 'f'};
    static final byte[] VIGNETTE = {(byte) 0xff, (byte) 0xd8, 'v', 'i', 'g'};

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;
    @Inject EventRepository eventRepository;
    @Inject GroupRepository groupRepository;
    @Inject GalleryPhotoRepository photoRepository;
    @Inject MediaRepository mediaRepository;
    @Inject MediaFileRepository fileRepository;
    @Inject ConfigurationRepository configurationRepository;
    @Inject FeatureSectorLeverRepository sectorLeverRepository;
    @Inject fr.fruityhedgeh0g.repositories.CarouselItemRepository carouselRepository;
    @Inject fr.fruityhedgeh0g.repositories.PostRepository postRepository;

    private UUID algrange;
    private UUID thionville;
    private UUID closed;
    private UUID edition;
    private UUID otherEvent;
    private UUID group;
    private int editionYear;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity a = sector("Algrange", false);
            SectorEntity t = sector("Thionville", false);
            SectorEntity c = sector("Fermé", false);
            algrange = a.getSectorId();
            thionville = t.getSectorId();
            closed = c.getSectorId();
            userRepository.persist(person(BUREAU, RoleEnum.BUREAU, a));
            userRepository.persist(person(OTHER_BUREAU, RoleEnum.BUREAU, t));
            userRepository.persist(person(BENEVOLE, RoleEnum.BENEVOLE, null));
            userRepository.persist(person(SUPER_ADMIN, RoleEnum.SUPER_ADMIN, null));
            edition = event(a, "Balade de la Rose");
            otherEvent = event(t, "Balade de Thionville");
            GroupEntity g = GroupEntity.builder().name("Galerie motards").sector(a).build();
            groupRepository.persist(g);
            group = g.getGroupId();
            editionYear = eventRepository.findById(edition).getStartDateTime().getYear();
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            carouselRepository.deleteAll();
            postRepository.delete("title like ?1", "Galerie %");
            eventRepository.update("media = null where eventId in ?1", List.of(edition, otherEvent));
            photoRepository.listAll().forEach(photoRepository::delete);
            photoRepository.flush();
            fileRepository.deleteAll();
            mediaRepository.deleteAll();
            configurationRepository.delete("name like ?1", "galerie.%");
            sectorLeverRepository.deleteAll();
            groupRepository.delete("groupId", group);
            eventRepository.delete("eventId in ?1", List.of(edition, otherEvent));
            userRepository.delete("userId in ?1", List.of(BUREAU, OTHER_BUREAU, BENEVOLE, SUPER_ADMIN).stream().map(UUID::fromString).toList());
            sectorRepository.delete("sectorId in ?1", List.of(algrange, thionville, closed));
        });
    }

    private SectorEntity sector(String name, boolean isClosed) {
        SectorEntity s = SectorEntity.builder().name("Galerie " + name + " " + UUID.randomUUID()).build();
        s.setClosed(isClosed);
        sectorRepository.persist(s);
        return s;
    }

    private static UserEntity person(String id, RoleEnum role, SectorEntity sector) {
        return UserEntity.builder().birthDate(LocalDate.of(1990, 1, 1)).userId(UUID.fromString(id)).firstName("Galerie").lastName(role.name())
                .role(role).phone("06 00 00 00 00").sector(sector).build();
    }

    private UUID event(SectorEntity sector, String name) {
        EventEntity e = new EventEntity();
        e.setName(name);
        e.setStatus(EventStatusEnum.OUVERT);
        e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
        e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
        e.setSector(sector);
        eventRepository.persist(e);
        return e.getEventId();
    }

    private static RequestSpecification photo(byte[] original) {
        return given().basePath("/api")
                .multiPart("original", "depart.jpg", original, "image/jpeg")
                .multiPart("affichage", "depart-affichage.jpg", AFFICHAGE, "image/jpeg")
                .multiPart("vignette", "depart-vignette.jpg", VIGNETTE, "image/jpeg");
    }

    /** The Bureau deposits a photo of the édition, showing the Groupe. */
    private ValidatableResponse depositOfEdition() {
        return photo(ORIGINAL).multiPart("eventId", edition.toString()).multiPart("groupId", group.toString())
                .multiPart(new MultiPartSpecBuilder("Le départ").controlName("alt").charset(StandardCharsets.UTF_8).build())
                .when().post("/galerie").then();
    }

    private ValidatableResponse album(int annee) {
        return given().basePath("/api").queryParam("sectorId", algrange).queryParam("annee", annee).when().get("/galerie").then();
    }

    private void makeVisible(String photoId, String visibilite) {
        QuarkusTransaction.requiringNew().run(() ->
                photoRepository.findById(UUID.fromString(photoId)).setVisibilite(fr.fruityhedgeh0g.enums.VisibiliteEnum.valueOf(visibilite)));
    }

    private String seededPhoto() {
        // Deposited as the Bureau would, then read as someone else
        return QuarkusTransaction.requiringNew().call(() -> {
            var media = new fr.fruityhedgeh0g.entities.medias.PhotoEntity();
            String key = UUID.randomUUID().toString();
            media.setFileKey(key);
            media.setContentType("PHOTO");
            media.setOriginalFilename("depart.jpg");
            media.setMimeType("image/jpeg");
            media.setFileSize(ORIGINAL.length);
            mediaRepository.persist(media);
            fileRepository.persist(new fr.fruityhedgeh0g.entities.medias.MediaFileEntity(key + "/original", "image/jpeg", ORIGINAL));
            fileRepository.persist(new fr.fruityhedgeh0g.entities.medias.MediaFileEntity(key + "/vignette", "image/jpeg", VIGNETTE));
            var photo = fr.fruityhedgeh0g.entities.GalleryPhotoEntity.builder().media(media)
                    .sector(sectorRepository.findById(algrange)).event(eventRepository.findById(edition)).annee(editionYear)
                    .depositedBy(UUID.fromString(BUREAU)).depositedAt(LocalDateTime.now()).build();
            photoRepository.persist(photo);
            return photo.getPhotoId().toString();
        });
    }

    private String mediaOf(String photoId) {
        return QuarkusTransaction.requiringNew().call(() -> photoRepository.findById(UUID.fromString(photoId)).getMedia().getMediaId().toString());
    }

    // --- Depositing ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauDepositsABrouillonWhoseEventGivesItsAnnee() {
        depositOfEdition().statusCode(200)
                .body("annee", equalTo(editionYear))
                .body("event.name", equalTo("Balade de la Rose"))
                .body("groups.name", hasItem("Galerie motards"))
                .body("visibilite", equalTo("brouillon"))
                .body("alt", equalTo("Le départ"));

        album(editionYear).statusCode(200).body("$", hasSize(1));
        given().basePath("/api").when().get("/galerie/albums").then().statusCode(200)
                .body("find { it.sectorId == '" + algrange + "' }.count", equalTo(1));
        // A Photo de la Galerie is not among the Medias used elsewhere
        given().basePath("/api").when().get("/medias").then().body("originalFilename", not(hasItem("depart.jpg")));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void withoutAnEventTheAnneeIsGiven() {
        photo(ORIGINAL).when().post("/galerie").then().statusCode(400);
        photo(ORIGINAL).multiPart("annee", "2019").when().post("/galerie").then().statusCode(200).body("annee", equalTo(2019));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void itsEventAndGroupesAreOfItsSecteur() {
        photo(ORIGINAL).multiPart("eventId", otherEvent.toString()).when().post("/galerie").then().statusCode(400);
        // The Bureau deposits in its own Secteur only
        photo(ORIGINAL).multiPart("sectorId", thionville.toString()).multiPart("annee", "2020").when().post("/galerie").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void aPhotoComesWithItsVariantsWithinTheLimitsTheSuperAdminSets() {
        given().basePath("/api").multiPart("original", "depart.jpg", ORIGINAL, "image/jpeg").multiPart("annee", "2020")
                .when().post("/galerie").then().statusCode(400);
        given().basePath("/api").when().get("/galerie/limites").then().statusCode(200)
                .body("maxPx", equalTo(3000)).body("maxMo", equalTo(5));

        QuarkusTransaction.requiringNew().run(() -> configurationRepository.persist(
                ConfigurationEntity.builder().name("galerie.photo.max-mo").value("1").build()));
        byte[] heavy = Arrays.copyOf(ORIGINAL, 1024 * 1024 + 1);
        photo(heavy).multiPart("annee", "2020").when().post("/galerie").then().statusCode(400);
    }

    // --- Seeing ---

    @Test
    void aVisitorSeesOnlyPubliquePhotos() {
        String photoId = seededPhoto();
        String mediaId = mediaOf(photoId);
        album(editionYear).statusCode(200).body("$", empty());
        given().basePath("/api").when().get("/medias/{id}/content", mediaId).then().statusCode(404);

        makeVisible(photoId, "INSCRITS");
        album(editionYear).body("$", empty());

        makeVisible(photoId, "PUBLIQUE");
        album(editionYear).body("$", hasSize(1)).body("[0].visibilite", nullValue());
        byte[] vignette = given().basePath("/api").queryParam("variante", "vignette").when().get("/medias/{id}/content", mediaId)
                .then().statusCode(200).header("Cache-Control", containsString("public")).extract().asByteArray();
        assertArrayEquals(VIGNETTE, vignette);
        // No display variant kept: its original is served
        assertArrayEquals(ORIGINAL, given().basePath("/api").queryParam("variante", "affichage").when()
                .get("/medias/{id}/content", mediaId).then().statusCode(200).extract().asByteArray());
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE))
    void someoneSignedInAlsoSeesPhotosReservedToInscrits() {
        String photoId = seededPhoto();
        album(editionYear).body("$", empty());
        makeVisible(photoId, "INSCRITS");
        album(editionYear).body("$", hasSize(1));
        given().basePath("/api").when().get("/medias/{id}/content", mediaOf(photoId)).then().statusCode(200)
                .header("Cache-Control", containsString("private"));
    }

    @Test
    @TestSecurity(user = "other", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = OTHER_BUREAU))
    void anotherSecteursBureauNeitherSeesNorChangesABrouillon() {
        String photoId = seededPhoto();
        album(editionYear).body("$", empty());
        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("photoIds", List.of(photoId), "visibilite", "publique"))
                .when().put("/galerie/visibilite").then().statusCode(403);
        given().basePath("/api").when().delete("/galerie/{id}", photoId).then().statusCode(403);
    }

    @Test
    void anAlbumNarrowsToAnEventOrAGroupe() {
        String photoId = seededPhoto();
        makeVisible(photoId, "PUBLIQUE");
        given().basePath("/api").queryParam("sectorId", algrange).queryParam("annee", editionYear).queryParam("eventId", edition)
                .when().get("/galerie").then().body("$", hasSize(1));
        given().basePath("/api").queryParam("sectorId", algrange).queryParam("annee", editionYear).queryParam("groupId", group)
                .when().get("/galerie").then().body("$", empty());
    }

    @Test
    void galeriePhotosOffForASecteurHidesItFromThePublic() {
        makeVisible(seededPhoto(), "PUBLIQUE");
        QuarkusTransaction.requiringNew().run(() -> sectorLeverRepository.persist(FeatureSectorLeverEntity.builder()
                .feature(FeatureEnum.GALERIE_PHOTOS.id()).sector(sectorRepository.findById(algrange)).isActive(false).build()));
        album(editionYear).statusCode(503);
        given().basePath("/api").when().get("/galerie/albums").then().statusCode(200)
                .body("sectorId", not(hasItem(algrange.toString())));
    }

    // --- Managing ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauDescribesItsPhotosAndMakesASelectionVisible() {
        String first = depositOfEdition().extract().path("photoId");
        String second = photo(ORIGINAL).multiPart("annee", "2020").when().post("/galerie").then().extract().path("photoId");

        given().basePath("/api").contentType(ContentType.JSON)
                .body(Map.of("alt", "L'arrivée", "annee", 2021, "groupIds", List.of(group.toString())))
                .when().patch("/galerie/{id}", second).then().statusCode(200)
                .body("annee", equalTo(2021)).body("event", nullValue()).body("alt", equalTo("L'arrivée"));

        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("photoIds", List.of(first, second), "visibilite", "publique"))
                .when().put("/galerie/visibilite").then().statusCode(200).body("visibilite", everyItemIs("publique"));
    }

    private static org.hamcrest.Matcher<Iterable<? extends String>> everyItemIs(String value) {
        return org.hamcrest.Matchers.everyItem(equalTo(value));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void theBureauDeletesAPhotoWithItsFiles() {
        String photoId = depositOfEdition().extract().path("photoId");
        String mediaId = mediaOf(photoId);
        given().basePath("/api").when().delete("/galerie/{id}", photoId).then().statusCode(204);
        given().basePath("/api").when().get("/medias/{id}/content", mediaId).then().statusCode(404);
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> fileRepository.count()));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void aSecteurFermesPhotosAreReadOnly() {
        String photoId = seededPhoto();
        QuarkusTransaction.requiringNew().run(() -> sectorRepository.findById(algrange).setClosed(true));
        album(editionYear).body("$", empty());
        given().basePath("/api").when().delete("/galerie/{id}", photoId).then().statusCode(400);
    }

    // --- Used on the public site (the image picker) ---

    private ValidatableResponse slideWith(String mediaId) {
        return given().basePath("/api").contentType(ContentType.JSON).body(Map.of("title", "Galerie diapo", "mediaId", mediaId))
                .when().post("/carousel").then();
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void onlyAPubliquePhotoIsUsedOnThePublicSite() {
        String photoId = depositOfEdition().extract().path("photoId");
        String mediaId = mediaOf(photoId);
        slideWith(mediaId).statusCode(400);

        makeVisible(photoId, "PUBLIQUE");
        slideWith(mediaId).statusCode(200).body("mediaId", equalTo(mediaId));
        given().basePath("/api").contentType(ContentType.JSON)
                .body(Map.of("title", "Galerie actualité", "content", "Le compte rendu", "bannerId", mediaId))
                .when().post("/posts").then().statusCode(200);

        album(editionYear).body("[0].usedBy", hasItem("Carrousel « Galerie diapo »"))
                .body("[0].usedBy", hasItem("Actualité « Galerie actualité »"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void aPhotoInUseStaysPubliqueAndIsNotDeleted() {
        String photoId = depositOfEdition().extract().path("photoId");
        makeVisible(photoId, "PUBLIQUE");
        slideWith(mediaOf(photoId)).statusCode(200);

        given().basePath("/api").contentType(ContentType.JSON).body(Map.of("photoIds", List.of(photoId), "visibilite", "inscrits"))
                .when().put("/galerie/visibilite").then().statusCode(400);
        given().basePath("/api").when().delete("/galerie/{id}", photoId).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU))
    void anImageOutsideTheGalerieIsDeletedOnceUnused() {
        String mediaId = given().basePath("/api").multiPart("file", "banniere.jpg", ORIGINAL, "image/jpeg")
                .when().post("/medias").then().statusCode(200).extract().path("mediaId");
        String slide = slideWith(mediaId).extract().path("id");
        given().basePath("/api").when().get("/medias").then().body("find { it.mediaId == '" + mediaId + "' }.usedBy", hasItem("Carrousel « Galerie diapo »"));
        given().basePath("/api").when().delete("/medias/{id}", mediaId).then().statusCode(400);

        given().basePath("/api").when().delete("/carousel/{id}", slide).then().statusCode(204);
        given().basePath("/api").when().delete("/medias/{id}", mediaId).then().statusCode(204);
        given().basePath("/api").when().get("/medias/{id}/content", mediaId).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "benevole", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BENEVOLE))
    void onlyTheBureauDeposits() {
        photo(ORIGINAL).multiPart("annee", "2020").when().post("/galerie").then().statusCode(403);
    }
}
