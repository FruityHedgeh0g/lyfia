package fr.fruityhedgeh0g.exceptions;

/** Keycloak could not be changed as asked; nothing was changed on the site either. */
public class KeycloakUnavailableException extends RuntimeException {
    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
