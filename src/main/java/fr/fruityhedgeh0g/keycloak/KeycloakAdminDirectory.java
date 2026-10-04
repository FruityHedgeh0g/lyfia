package fr.fruityhedgeh0g.keycloak;

import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;

import java.util.Optional;
import java.util.UUID;

/** Looks people up through the Keycloak admin API. */
@ApplicationScoped
@UnlessBuildProfile("dev")
public class KeycloakAdminDirectory implements KeycloakDirectory {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realmName;

    @Override
    public Optional<KeycloakPerson> findByUsername(String username) {
        return keycloak.realm(realmName).users().searchByUsername(username, true).stream()
                .findFirst()
                .map(user -> new KeycloakPerson(UUID.fromString(user.getId()), user.getFirstName(), user.getLastName()));
    }
}
