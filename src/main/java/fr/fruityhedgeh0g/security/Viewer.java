package fr.fruityhedgeh0g.security;

import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.UserRepository;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.UUID;

/** What the person making the current request may see, beyond what their Role opens. */
@ApplicationScoped
public class Viewer {

    @Inject
    Instance<SecurityIdentity> identity;

    @Inject
    UserRepository userRepository;

    /** Only the Super admin sees a Secteur fermé, its Groupes and its Events (ADR 0003); nobody outside a request. */
    public boolean seesClosedSecteurs() {
        return Arc.container().requestContext().isActive() && identity.get().hasRole(RoleEnum.SUPER_ADMIN.id());
    }

    /** The Bureau and above prepare the content the public sees (Posts, the carousel, the medias). */
    public boolean preparesContent() {
        return Arc.container().requestContext().isActive() && identity.get().hasRole(RoleEnum.BUREAU.id());
    }

    /** Who makes the current request, for the logs: the person's id, or "anonymous". */
    public String describe() {
        if (!Arc.container().requestContext().isActive()) return "outside a request";
        SecurityIdentity current = identity.get();
        if (current.isAnonymous()) return "anonymous";
        return DatabaseRoleAugmentor.subjectOf(current).map(UUID::toString).orElse(current.getPrincipal().getName());
    }

    /**
     * The Secteurs this person manages (ADR 0004). Outside a request (internal calls, service tests)
     * nothing is scoped: the checks belong to what a person asks for.
     */
    public SecteurScope scope() {
        if (!Arc.container().requestContext().isActive()) return SecteurScope.EVERY_SECTEUR;
        SecurityIdentity current = identity.get();
        if (current.hasRole(RoleEnum.SUPER_ADMIN.id())) return SecteurScope.EVERY_SECTEUR;
        return DatabaseRoleAugmentor.subjectOf(current)
                .flatMap(userRepository::findByIdOptional)
                .map(UserEntity::getSector)
                .map(SecteurScope::of)
                .orElse(SecteurScope.of(null));
    }
}
