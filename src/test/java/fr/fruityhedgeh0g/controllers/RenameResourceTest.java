package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalUserService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only an Admin corrects a person's names, for their Secteur's Inscrits; nobody renames themselves (ADR 0007). */
@QuarkusTest
@TestHTTPEndpoint(UserController.class)
class RenameResourceTest {

    static final String SUPER_ADMIN_ID = "00000000-0000-0000-0007-000000000001";
    static final String ADMIN_ID = "00000000-0000-0000-0007-000000000002";
    static final String BUREAU_ID = "00000000-0000-0000-0007-000000000003";
    static final String MEMBRE_ID = "00000000-0000-0000-0007-000000000004";
    static final String RIDER_ID = "00000000-0000-0000-0007-000000000005";
    static final String STRANGER_ID = "00000000-0000-0000-0007-000000000006";
    static final String ELSEWHERE_ID = "00000000-0000-0000-0007-000000000007";

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;
    @Inject EventRepository eventRepository;
    @Inject EventRegistrationRepository registrationRepository;
    @Inject InternalUserService internalUserService;

    private UUID sector;
    private UUID otherSector;
    private UUID ride;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity mine = SectorEntity.builder().name("Test Rename " + UUID.randomUUID()).build();
            SectorEntity other = SectorEntity.builder().name("Test Rename other " + UUID.randomUUID()).build();
            sectorRepository.persist(mine);
            sectorRepository.persist(other);
            sector = mine.getSectorId();
            otherSector = other.getSectorId();

            EventEntity event = new EventEntity();
            event.setName("Test Balade");
            event.setStatus(EventStatusEnum.OUVERT);
            event.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(10));
            event.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).minusDays(9));
            event.setSector(mine);
            eventRepository.persist(event);
            ride = event.getEventId();

            person(SUPER_ADMIN_ID, RoleEnum.SUPER_ADMIN, null);
            person(ADMIN_ID, RoleEnum.ADMIN, mine);
            person(BUREAU_ID, RoleEnum.BUREAU, mine);
            person(MEMBRE_ID, RoleEnum.MEMBRE, mine);
            UserEntity rider = person(RIDER_ID, RoleEnum.BENEVOLE, null);
            person(STRANGER_ID, RoleEnum.BENEVOLE, null);
            person(ELSEWHERE_ID, RoleEnum.MEMBRE, other);
            registrationRepository.persist(EventRegistrationEntity.conducteur(event, rider, false));
        });
    }

    private UserEntity person(String id, RoleEnum role, SectorEntity sector) {
        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(UUID.fromString(id)).firstName("Test").lastName(role.name()).role(role).sector(sector).build();
        userRepository.persist(user);
        return user;
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.eventId", ride);
            eventRepository.deleteById(ride);
            List.of(SUPER_ADMIN_ID, ADMIN_ID, BUREAU_ID, MEMBRE_ID, RIDER_ID, STRANGER_ID, ELSEWHERE_ID)
                    .forEach(id -> userRepository.deleteById(UUID.fromString(id)));
            eventRepository.flush();
            sectorRepository.deleteById(sector);
            sectorRepository.deleteById(otherSector);
        });
    }

    private ValidatableResponse rename(String personId, String firstName, String lastName) {
        return given().contentType(ContentType.JSON)
                .body("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}")
                .when().patch("/" + personId).then();
    }

    private String namesOf(String id) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = userRepository.findById(UUID.fromString(id));
            return user.getFirstName() + " " + user.getLastName();
        });
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminRenamesAPersonOfTheirSecteur() {
        rename(MEMBRE_ID, " Camille ", "Martin").statusCode(200).body("firstName", equalTo("Camille"));
        assertEquals("Camille Martin", namesOf(MEMBRE_ID));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminRenamesABenevoleWhoRodeWithTheirSecteur() {
        rename(RIDER_ID, "Camille", "Martin").statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void anAdminDoesNotRenameOutsideTheirInscrits() {
        rename(STRANGER_ID, "Camille", "Martin").statusCode(403);
        rename(ELSEWHERE_ID, "Camille", "Martin").statusCode(403);
        assertEquals("Test MEMBRE", namesOf(ELSEWHERE_ID));
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void nobodyRenamesThemselves() {
        rename(ADMIN_ID, "Camille", "Martin").statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void aPersonHasBothNames() {
        rename(MEMBRE_ID, " ", "Martin").statusCode(400);
    }

    @Test
    @TestSecurity(user = "superadmin", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = SUPER_ADMIN_ID))
    void theSuperAdminRenamesAnyone() {
        rename(ELSEWHERE_ID, "Camille", "Martin").statusCode(200);
        rename(STRANGER_ID, "Camille", "Martin").statusCode(200);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauDoesNotRename() {
        rename(MEMBRE_ID, "Camille", "Martin").statusCode(403);
    }

    @Test
    @TestSecurity(user = "membre", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = MEMBRE_ID))
    void aPersonEditsOnlyTheirPhoneInMonEspace() {
        given().contentType(ContentType.JSON)
                .body("{\"firstName\":\"Camille\",\"lastName\":\"Martin\",\"phone\":\" 06 12 34 56 78 \"}")
                .when().patch("/me").then().statusCode(200)
                .body("phone", equalTo("06 12 34 56 78"))
                .body("firstName", equalTo("Test"));
        assertEquals("Test MEMBRE", namesOf(MEMBRE_ID));
    }

    @Test
    void keycloakFillsOnlyMissingNames() {
        UUID id = UUID.fromString(MEMBRE_ID);
        internalUserService.doUpdate(UserDto.builder().userId(id).firstName("Camille").lastName("Martin").build());
        assertEquals("Test MEMBRE", namesOf(MEMBRE_ID));

        QuarkusTransaction.requiringNew().run(() -> userRepository.findById(id).setLastName(""));
        internalUserService.doUpdate(UserDto.builder().userId(id).firstName("Camille").lastName("Martin").build());
        assertEquals("Test Martin", namesOf(MEMBRE_ID));
    }
}
