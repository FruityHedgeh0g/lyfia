package fr.fruityhedgeh0g.keycloak;

import fr.fruityhedgeh0g.exceptions.KeycloakUnavailableException;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

/** The realm's registration setting held in memory instead of Keycloak; can be told to fail. */
@Mock
@ApplicationScoped
public class FakeKeycloakRegistration implements KeycloakRegistration {

    private volatile boolean allowed = true;
    private volatile boolean failing;

    @Override
    public void allowSelfRegistration(boolean allowed) {
        if (failing) throw new KeycloakUnavailableException("Keycloak unreachable", new IllegalStateException());
        this.allowed = allowed;
    }

    public boolean allowed() {
        return allowed;
    }

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        allowed = true;
        failing = false;
    }
}
