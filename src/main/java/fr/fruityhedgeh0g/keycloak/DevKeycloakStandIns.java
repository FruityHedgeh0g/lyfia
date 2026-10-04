package fr.fruityhedgeh0g.keycloak;

import fr.fruityhedgeh0g.enums.RoleEnum;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/**
 * Development has no Keycloak: these stand in for the admin client in dev builds only, and say in the logs what
 * Keycloak would have been asked. People are tried out through dev personas (DevPersonaAuthentication).
 */
public final class DevKeycloakStandIns {

    private DevKeycloakStandIns() {
    }

    @ApplicationScoped
    @IfBuildProfile("dev")
    public static class Directory implements KeycloakDirectory {
        @Override
        public Optional<KeycloakPerson> findByUsername(String username) {
            Log.infof("Dev, no Keycloak: nobody is looked up as %s", username);
            return Optional.empty();
        }
    }

    @ApplicationScoped
    @IfBuildProfile("dev")
    public static class RoleMirror implements KeycloakRoleMirror {
        @Override
        public void setRoleGroup(UUID personId, RoleEnum role) {
            Log.infof("Dev, no Keycloak: %s would join the group %s", personId, role.id());
        }
    }

    @ApplicationScoped
    @IfBuildProfile("dev")
    public static class Registration implements KeycloakRegistration {
        @Override
        public void allowSelfRegistration(boolean allowed) {
            Log.infof("Dev, no Keycloak: registration would be %s", allowed ? "opened" : "closed");
        }
    }
}
