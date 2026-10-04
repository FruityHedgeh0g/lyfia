package fr.fruityhedgeh0g.keycloak;

import fr.fruityhedgeh0g.enums.RoleEnum;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors a Role through the Keycloak admin API. Each Role has a top-level
 * group named after its id ({@code benevole}, {@code membre}, ...), which must
 * exist in the realm.
 */
@ApplicationScoped
@UnlessBuildProfile("dev")
public class KeycloakAdminRoleMirror implements KeycloakRoleMirror {

    private static final Set<String> ROLE_GROUPS = Arrays.stream(RoleEnum.values())
            .map(RoleEnum::id)
            .collect(Collectors.toSet());

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realmName;

    @Override
    public void setRoleGroup(UUID personId, RoleEnum role) {
        RealmResource realm = keycloak.realm(realmName);
        UserResource person = realm.users().get(personId.toString());

        String groupId = realm.groups().groups(role.id(), true, 0, 10, true).stream()
                .filter(group -> group.getName().equals(role.id()))
                .findFirst()
                .map(GroupRepresentation::getId)
                .orElseThrow(() -> new IllegalStateException("Missing Keycloak group: " + role.id()));

        // Join first: if leaving fails, the person keeps at least the right group
        person.joinGroup(groupId);
        person.groups().stream()
                .filter(group -> ROLE_GROUPS.contains(group.getName()) && !group.getId().equals(groupId))
                .forEach(group -> person.leaveGroup(group.getId()));
    }
}
