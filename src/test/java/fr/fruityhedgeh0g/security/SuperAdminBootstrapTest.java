package fr.fruityhedgeh0g.security;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakDirectory;
import fr.fruityhedgeh0g.keycloak.FakeKeycloakRoleMirror;
import fr.fruityhedgeh0g.keycloak.KeycloakDirectory.KeycloakPerson;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SuperAdminBootstrapTest {

    @Inject
    SuperAdminBootstrap bootstrap;

    @Inject
    FakeKeycloakDirectory directory;

    @Inject
    FakeKeycloakRoleMirror mirror;

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    private final UUID personId = UUID.randomUUID();
    private UUID sectorId;

    @BeforeEach
    void setUp() {
        directory.reset();
        mirror.reset();
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.deleteById(personId);
            if (sectorId != null) sectorRepository.deleteById(sectorId);
        });
    }

    @Test
    void createsThePersonKeycloakNeverAnnounced() {
        directory.register("julien", new KeycloakPerson(personId, "Julien", "Peynot"));

        assertTrue(bootstrap.appoint("julien"));

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId));
        assertEquals(RoleEnum.SUPER_ADMIN, user.getRole());
        assertEquals("Julien", user.getFirstName());
        assertEquals("Peynot", user.getLastName());
        assertNull(user.getSector());
        assertEquals(List.of(new FakeKeycloakRoleMirror.Call(personId, RoleEnum.SUPER_ADMIN)), mirror.calls());
    }

    @Test
    void promotesAnExistingPersonOutOfTheirSecteur() {
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity sector = SectorEntity.builder().name("Bootstrap " + personId).build();
            sectorRepository.persist(sector);
            sectorId = sector.getSectorId();
            userRepository.persist(UserEntity.builder().userId(personId).firstName("Julien").lastName("Peynot")
                    .role(RoleEnum.BUREAU).bureauFunction(BureauFunctionEnum.PRESIDENT).sector(sector).build());
        });
        directory.register("julien", new KeycloakPerson(personId, "Julien", "Peynot"));

        assertTrue(bootstrap.appoint("julien"));

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId));
        assertEquals(RoleEnum.SUPER_ADMIN, user.getRole());
        assertNull(user.getSector());
        assertFalse(user.isPresident());
    }

    @Test
    void runsAgainWithoutChange() {
        directory.register("julien", new KeycloakPerson(personId, "Julien", "Peynot"));

        assertTrue(bootstrap.appoint("julien"));
        assertTrue(bootstrap.appoint("julien"));

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId));
        assertEquals(RoleEnum.SUPER_ADMIN, user.getRole());
    }

    @Test
    void fallsBackToTheUsernameWithoutNamesInKeycloak() {
        directory.register("julien", new KeycloakPerson(personId, null, null));

        assertTrue(bootstrap.appoint("julien"));

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId));
        assertEquals("julien", user.getFirstName());
        assertEquals("", user.getLastName());
    }

    @Test
    void unknownUsernameAppointsNobody() {
        assertFalse(bootstrap.appoint("nobody"));
        assertTrue(mirror.calls().isEmpty());
    }

    @Test
    void keycloakDownDoesNotStopTheApplication() {
        directory.failing(true);

        assertFalse(bootstrap.appoint("julien"));
        assertNull(QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId)));
    }

    @Test
    void aFailedMirrorKeepsTheDatabaseRole() {
        directory.register("julien", new KeycloakPerson(personId, "Julien", "Peynot"));
        mirror.failing(true);

        assertTrue(bootstrap.appoint("julien"));

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> userRepository.findById(personId));
        assertEquals(RoleEnum.SUPER_ADMIN, user.getRole());
    }
}
