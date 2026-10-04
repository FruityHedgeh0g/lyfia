package fr.fruityhedgeh0g.security;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.BaseAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.jose4j.jwt.JwtClaims;

import java.util.Set;
import java.util.UUID;

/**
 * Development personas, without Keycloak: a request carrying the persona cookie acts as that persona's person, as if
 * they had logged in. Built into dev and test builds only ({@code @IfBuildProfile}): a production build does not
 * contain it, so no cookie can do anything there. The principal is the kind OIDC gives, so the injected token's
 * subject, the Role read from the database (DatabaseRoleAugmentor) and the Secteur scope all work unchanged.
 */
public final class DevPersonaAuthentication {

    /** The cookie holding the persona person's id (DevPersonaController sets and clears it). */
    public static final String COOKIE = "lyfia-dev-persona";

    private DevPersonaAuthentication() {
    }

    /** A persona person to act as. */
    public static final class Request extends BaseAuthenticationRequest {
        private final UUID personId;

        public Request(UUID personId) {
            this.personId = personId;
        }

        public UUID personId() {
            return personId;
        }
    }

    /** Reads the persona cookie; without one, the request stays anonymous (or goes on to OIDC, in tests). */
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "test"})
    public static class Mechanism implements HttpAuthenticationMechanism {

        @Override
        public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
            UUID personId = personaOf(context);
            if (personId == null) return Uni.createFrom().nullItem();
            return identityProviderManager.authenticate(HttpSecurityUtils.setRoutingContextAttribute(new Request(personId), context));
        }

        private static UUID personaOf(RoutingContext context) {
            Cookie cookie = context.request().getCookie(COOKIE);
            if (cookie == null || cookie.getValue().isBlank()) return null;
            try {
                return UUID.fromString(cookie.getValue());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** No challenge of its own: a persona is chosen on the site, never asked for. */
        @Override
        public Uni<ChallengeData> getChallenge(RoutingContext context) {
            return Uni.createFrom().nullItem();
        }

        @Override
        public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
            return Set.of(Request.class);
        }

        @Override
        public int getPriority() {
            return DEFAULT_PRIORITY + 1000;
        }
    }

    /** The persona's identity, with an OIDC-like principal whose subject is the person's id. */
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "test"})
    public static class Provider implements IdentityProvider<Request> {

        @Override
        public Class<Request> getRequestType() {
            return Request.class;
        }

        @Override
        public Uni<SecurityIdentity> authenticate(Request request, AuthenticationRequestContext context) {
            JwtClaims claims = new JwtClaims();
            claims.setSubject(request.personId().toString());
            claims.setClaim("preferred_username", "persona-" + request.personId());
            AccessTokenCredential credential = new AccessTokenCredential("dev-persona");
            return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                    .setPrincipal(new OidcJwtCallerPrincipal(claims, credential))
                    .addCredential(credential)
                    .build());
        }
    }
}
