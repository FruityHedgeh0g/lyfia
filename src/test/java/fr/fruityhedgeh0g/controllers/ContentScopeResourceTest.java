package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.CarouselItemEntity;
import fr.fruityhedgeh0g.entities.PostEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.CarouselItemRepository;
import fr.fruityhedgeh0g.repositories.PostRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actualités and the Carrousel per Secteur (ADR 0004): a Post and a slide belong to a Secteur, whose Bureau manages
 * them; the Super admin manages them all, and those of the whole site. Everyone still reads what is published.
 */
@QuarkusTest
class ContentScopeResourceTest {

    static final String BUREAU_A = "00000000-0000-0000-0026-000000000001";
    static final String BUREAU_B = "00000000-0000-0000-0026-000000000002";
    static final String SUPER_ADMIN = "00000000-0000-0000-0026-000000000003";

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;
    @Inject PostRepository postRepository;
    @Inject CarouselItemRepository itemRepository;

    private UUID algrange;
    private UUID thionville;
    private UUID closed;
    private UUID draftA;
    private UUID publishedA;
    private UUID publishedClosed;
    private UUID slideA1;
    private UUID slideA2;
    private UUID slideB;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity a = sector("Algrange", false);
            SectorEntity b = sector("Thionville", false);
            SectorEntity c = sector("Fermé", true);
            algrange = a.getSectorId();
            thionville = b.getSectorId();
            closed = c.getSectorId();
            userRepository.persist(person(BUREAU_A, RoleEnum.BUREAU, a));
            userRepository.persist(person(BUREAU_B, RoleEnum.BUREAU, b));
            userRepository.persist(person(SUPER_ADMIN, RoleEnum.SUPER_ADMIN, null));
            draftA = post("Scope brouillon A", PostStatusEnum.BROUILLON, a);
            publishedA = post("Scope publié A", PostStatusEnum.PUBLIE, a);
            publishedClosed = post("Scope publié fermé", PostStatusEnum.PUBLIE, c);
            slideA1 = slide("A1", 1, a);
            slideB = slide("B", 2, b);
            slideA2 = slide("A2", 3, a);
        });
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            postRepository.delete("title like ?1", "Scope %");
            itemRepository.deleteAll();
            userRepository.delete("userId in ?1", List.of(BUREAU_A, BUREAU_B, SUPER_ADMIN).stream().map(UUID::fromString).toList());
            sectorRepository.delete("sectorId in ?1", List.of(algrange, thionville, closed));
        });
    }

    private SectorEntity sector(String name, boolean isClosed) {
        SectorEntity s = SectorEntity.builder().name("Scope " + name + " " + UUID.randomUUID()).build();
        s.setClosed(isClosed);
        sectorRepository.persist(s);
        return s;
    }

    private static UserEntity person(String id, RoleEnum role, SectorEntity sector) {
        return UserEntity.builder().userId(UUID.fromString(id)).firstName("Scope").lastName(role.name()).role(role).sector(sector).build();
    }

    private UUID post(String title, PostStatusEnum status, SectorEntity sector) {
        PostEntity post = new PostEntity();
        post.setTitle(title);
        post.setContent("Contenu");
        post.setStatus(status);
        post.setSector(sector);
        postRepository.persist(post);
        return post.getPostId();
    }

    private UUID slide(String title, int position, SectorEntity sector) {
        CarouselItemEntity item = new CarouselItemEntity();
        item.setTitle(title);
        item.setPosition(position);
        item.setSector(sector);
        itemRepository.persist(item);
        return item.getItemId();
    }

    private static ValidatableResponse createPost(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).when().post("/api/posts").then();
    }

    private static ValidatableResponse publish(UUID postId) {
        return given().contentType(ContentType.JSON).body(Map.of("status", "publie")).when().put("/api/posts/{id}/status", postId).then();
    }

    // --- Actualités ---

    @Test
    void everyoneReadsWhatIsPublishedButNothingOfASecteurFerme() {
        given().when().get("/api/posts").then().statusCode(200)
                .body("postId", hasItem(publishedA.toString()))
                .body("postId", not(hasItem(draftA.toString())))
                .body("postId", not(hasItem(publishedClosed.toString())))
                .body("find { it.postId == '" + publishedA + "' }.sectorId", equalTo(algrange.toString()));
        given().when().get("/api/posts/{id}", publishedClosed).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau-a", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_A))
    void aBureauMemberWritesForTheirOwnSecteur() {
        createPost(Map.of("title", "Scope nouveau", "content", "Contenu", "sectorId", thionville.toString()))
                .statusCode(200).body("sectorId", equalTo(algrange.toString()));
        publish(draftA).statusCode(200);
        given().when().get("/api/posts?managed=true").then().statusCode(200)
                .body("title", containsInAnyOrder("Scope nouveau", "Scope brouillon A", "Scope publié A"));
    }

    @Test
    @TestSecurity(user = "bureau-b", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_B))
    void anotherSecteursBureauNeitherSeesItsDraftsNorManagesItsPosts() {
        given().when().get("/api/posts").then().statusCode(200)
                .body("postId", hasItem(publishedA.toString()))
                .body("postId", not(hasItem(draftA.toString())));
        given().when().get("/api/posts/{id}", draftA).then().statusCode(404);
        publish(draftA).statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("postId", publishedA.toString(), "title", "Scope piraté"))
                .when().patch("/api/posts").then().statusCode(403);
        given().when().get("/api/posts?managed=true").then().statusCode(200).body("postId", not(hasItem(publishedA.toString())));
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void theSuperAdminWritesForAnySecteurOrTheWholeSite() {
        createPost(Map.of("title", "Scope pour B", "content", "Contenu", "sectorId", thionville.toString()))
                .statusCode(200).body("sectorId", equalTo(thionville.toString()));
        createPost(Map.of("title", "Scope pour tous", "content", "Contenu")).statusCode(200).body("sectorId", nullValue());
        createPost(Map.of("title", "Scope fermé", "content", "Contenu", "sectorId", closed.toString())).statusCode(400);
        publish(draftA).statusCode(200);
        given().when().get("/api/posts/{id}", publishedClosed).then().statusCode(200);
    }

    // --- Carrousel ---

    @Test
    void everyoneSeesTheActiveSlidesOfEverySecteur() {
        given().when().get("/api/carousel").then().statusCode(200)
                .body("title", contains("A1", "B", "A2"))
                .body("[1].sectorId", equalTo(thionville.toString()));
    }

    @Test
    @TestSecurity(user = "bureau-a", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_A))
    void aBureauMemberManagesTheirSecteursSlides() {
        given().contentType(ContentType.JSON).body("{\"title\":\"A3\",\"active\":true,\"sectorId\":\"" + thionville + "\"}")
                .when().post("/api/carousel").then().statusCode(200).body("sectorId", equalTo(algrange.toString()));
        given().when().get("/api/carousel?managed=true").then().statusCode(200).body("title", contains("A1", "A2", "A3"));
        // Ordering happens among the Secteur's own slides: A2 goes before A1, Thionville's slide stays put
        given().when().post("/api/carousel/" + slideA2 + "/move/up").then().statusCode(200).body("title", contains("A2", "A1", "A3"));
        given().when().get("/api/carousel").then().body("title", contains("A2", "B", "A1", "A3"));
    }

    @Test
    @TestSecurity(user = "bureau-b", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_B))
    void anotherSecteursBureauCannotTouchThem() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Piraté\",\"active\":true}")
                .when().put("/api/carousel/" + slideA1).then().statusCode(403);
        given().when().delete("/api/carousel/" + slideA1).then().statusCode(403);
        given().when().post("/api/carousel/" + slideA1 + "/move/down").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN))
    void theSuperAdminManagesEverySlide() {
        given().when().get("/api/carousel?managed=true").then().statusCode(200).body("title", contains("A1", "B", "A2"));
        given().contentType(ContentType.JSON).body("{\"title\":\"Tous\",\"active\":true}")
                .when().post("/api/carousel").then().statusCode(200).body("sectorId", nullValue());
        given().when().delete("/api/carousel/" + slideB).then().statusCode(204);
    }
}
