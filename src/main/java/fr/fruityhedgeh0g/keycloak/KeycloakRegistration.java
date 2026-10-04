package fr.fruityhedgeh0g.keycloak;

/** Opens or closes the realm's own registration form, where people register (Inscription sur le site, ADR 0009). */
public interface KeycloakRegistration {

    /** @throws fr.fruityhedgeh0g.exceptions.KeycloakUnavailableException when Keycloak cannot be changed */
    void allowSelfRegistration(boolean allowed);
}
