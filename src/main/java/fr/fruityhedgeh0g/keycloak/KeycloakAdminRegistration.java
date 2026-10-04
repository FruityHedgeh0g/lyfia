package fr.fruityhedgeh0g.keycloak;

import fr.fruityhedgeh0g.exceptions.KeycloakUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;

/** Switches the realm's "User registration" setting through the Keycloak admin API (needs manage-realm). */
@ApplicationScoped
public class KeycloakAdminRegistration implements KeycloakRegistration {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realmName;

    @Override
    public void allowSelfRegistration(boolean allowed) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            RealmRepresentation representation = realm.toRepresentation();
            representation.setRegistrationAllowed(allowed);
            realm.update(representation);
        } catch (RuntimeException e) {
            throw new KeycloakUnavailableException("Could not " + (allowed ? "open" : "close") + " registration on the realm " + realmName, e);
        }
    }
}
