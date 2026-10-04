package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.PostEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.PostRepository;
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
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/** Posts: written by the Bureau as Brouillon, then published; only Publié Posts are seen below the Bureau. */
@QuarkusTest
@TestHTTPEndpoint(PostController.class)
public class PostResourceTest {

    static final String BUREAU_ID = "00000000-0000-0000-0011-000000000005";
    static final String CHEF_ID = "00000000-0000-0000-0011-000000000004";
    static final String MEMBRE_ID = "00000000-0000-0000-0011-000000000003";

    @Inject
    UserRepository userRepository;

    @Inject
    PostRepository postRepository;

    @Inject
    SectorRepository sectorRepository;

    private UUID sector;
    private UUID draft;
    private UUID published;

    @BeforeEach
    void seed() {
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef");
        persistPerson(MEMBRE_ID, RoleEnum.MEMBRE, "Membre");
        sector = QuarkusTransaction.requiringNew().call(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            return s.getSectorId();
        });
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
        draft = persistPost("Test brouillon", PostStatusEnum.BROUILLON);
        published = persistPost("Test publié", PostStatusEnum.PUBLIE);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            postRepository.delete("title like ?1", "Test %");
            List.of(BUREAU_ID, CHEF_ID, MEMBRE_ID).forEach(id -> userRepository.deleteById(UUID.fromString(id)));
            sectorRepository.deleteById(sector);
        });
    }

    private void persistPerson(String id, RoleEnum role, String lastName) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().userId(UUID.fromString(id)).firstName("Test").lastName(lastName).role(role).build()));
    }

    private UUID persistPost(String title, PostStatusEnum status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PostEntity post = new PostEntity();
            post.setTitle(title);
            post.setContent("Contenu de " + title);
            post.setStatus(status);
            post.setAuthor(userRepository.findById(UUID.fromString(BUREAU_ID)));
            post.setSector(sectorRepository.findById(sector));
            postRepository.persist(post);
            return post.getPostId();
        });
    }

    private ValidatableResponse create(String title) {
        return given().contentType(ContentType.JSON).body(Map.of("title", title, "content", "Un contenu"))
                .when().post().then();
    }

    private ValidatableResponse changeStatus(UUID postId, String status) {
        return given().contentType(ContentType.JSON).body(Map.of("status", status))
                .when().put("/{id}/status", postId).then();
    }

    // --- Reading ---

    @Test
    void anonymousVisiteurSeesOnlyPublishedPosts() {
        given().when().get().then().statusCode(200)
                .body("postId", hasItem(published.toString()))
                .body("postId", not(hasItem(draft.toString())));
        given().when().get("/{id}", published).then().statusCode(200)
                .body("status", equalTo("publie"))
                .body("author.lastName", equalTo("Bureau"));
        given().when().get("/{id}", draft).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void belowTheBureauDraftsStayHidden() {
        given().when().get().then().statusCode(200).body("postId", not(hasItem(draft.toString())));
        given().when().get("/{id}", draft).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauSeesDraftsToo() {
        given().when().get().then().statusCode(200)
                .body("postId", hasItem(draft.toString()))
                .body("find { it.postId == '" + draft + "' }.status", equalTo("brouillon"));
        given().when().get("/{id}", draft).then().statusCode(200);
    }

    // --- Writing ---

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void aNewPostIsABrouillonWrittenByItsCreator() {
        create("Test nouveau").statusCode(200)
                .body("title", equalTo("Test nouveau"))
                .body("status", equalTo("brouillon"))
                .body("author.userId", equalTo(BUREAU_ID))
                .body("sectorId", equalTo(sector.toString()));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauEditsAPost() {
        given().contentType(ContentType.JSON)
                .body(Map.of("postId", draft.toString(), "title", "Test renommé", "content", "Nouveau contenu"))
                .when().patch().then().statusCode(200)
                .body("title", equalTo("Test renommé"))
                .body("status", equalTo("brouillon"));
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauPublishesAndUnpublishes() {
        changeStatus(draft, "publie").statusCode(200).body("status", equalTo("publie"));
        changeStatus(draft, "brouillon").statusCode(200).body("status", equalTo("brouillon"));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void belowTheBureauNothingIsWrittenOrPublished() {
        create("Test refusé").statusCode(403);
        changeStatus(draft, "publie").statusCode(403);
        changeStatus(published, "brouillon").statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("postId", draft.toString(), "title", "Test piraté"))
                .when().patch().then().statusCode(403);
    }

    @Test
    void anonymousVisiteurCannotWrite() {
        create("Test anonyme").statusCode(401);
        changeStatus(draft, "publie").statusCode(401);
    }
}
