package fr.fruityhedgeh0g.services;

import java.time.LocalDate;
import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.userDtos.NamesDto;
import fr.fruityhedgeh0g.dtos.userDtos.ProfileDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.ForbiddenRoleChangeException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.NotImplementedYetException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.keycloak.KeycloakRoleMirror;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.services.interfaces.UserService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalGroupService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalSectorService;
import fr.fruityhedgeh0g.utilities.mappers.UserMapper;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.util.*;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class UserServiceImpl implements UserService {

    @Inject
    UserRepository userRepository;

    @Inject
    UserMapper userMapper;

    @Inject
    KeycloakRoleMirror keycloakRoleMirror;

    @Inject
    InternalGroupService internalGroupService;

    @Inject
    InternalSectorService internalSectorService;

    @Inject
    fr.fruityhedgeh0g.security.Viewer viewer;

    //Using UUID to test the existence of the user is acceptable because it is based on an external system (Keycloak)

    @Override
    public List<UserDto> listAll() {
        // The Inscrits list: every person for the Super admin, a Secteur's Inscrits for its Bureau and Admin (ADR 0004)
        var scope = viewer.scope();
        List<UserEntity> persons = scope.everySecteur() ? userRepository.listAll()
                : scope.sectorId() == null ? List.of() : userRepository.listInscritsOf(scope.sectorId());
        return persons
                .stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Override
    public UserDto getById(UUID userId) {
        return userMapper.toDto(
                userRepository.findByIdOptional(userId)
                        .orElseThrow(() -> new UnknownResourceException("User not found: "+userId))
        );

    }

    @Override
    @Transactional
    public UserDto getOrJoin(UUID personId, String firstName, String lastName, String email) {
        UserEntity person = userRepository.findByIdOptional(personId).orElseGet(() -> {
            UserEntity joined = UserEntity.builder()
                    .userId(personId)
                    .firstName(isBlank(firstName) ? "" : firstName.trim())
                    .lastName(isBlank(lastName) ? "" : lastName.trim())
                    .role(RoleEnum.BENEVOLE)
                    .build();
            userRepository.persist(joined);
            return joined;
        });
        // The e-mail is the Keycloak account's: copied at each sign-in
        if (!isBlank(email) && !email.trim().equals(person.getEmail())) person.setEmail(email.trim());
        return userMapper.toDto(person);
    }

    @Override
    public UserDto changeRole(UUID actorId, UUID personId, RoleEnum role, UUID sectorId) {
        // Committed on its own before the Keycloak call, so a mirror failure cannot roll it back (ADR 0002)
        UserDto changed = QuarkusTransaction.requiringNew().call(() -> {
            UserEntity actor = userRepository.findByIdOptional(actorId)
                    .orElseThrow(() -> new ForbiddenRoleChangeException("Unknown actor: " + actorId));
            RoleEnum actorRole = actor.getRole();
            if (actorId.equals(personId))
                throw new ForbiddenRoleChangeException("Nobody changes their own Role.");

            UserEntity person = userRepository.findByIdOptional(personId)
                    .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
            if (!actorRole.maySetRole(person.getRole(), role))
                throw new ForbiddenRoleChangeException(actorRole.id() + " cannot set " + person.getRole().id() + " to " + role.id());

            SectorEntity sector = sectorAfter(actor, person, role, sectorId);
            boolean changesSector = person.getSector() != null && !person.belongsTo(sector);

            person.changeRole(role);
            person.setSector(sector);
            // Sitting on a Bureau is within one Secteur: an Admin appointed to another one leaves it
            if (changesSector && role == RoleEnum.ADMIN)
                person.setBureauFunction(null);
            // A Chef's Affectation ends with the title, or when they leave their Groupe's Secteur
            if (!role.canLeadGroupe() || changesSector)
                internalGroupService.doEndAffectationOf(personId);
            return userMapper.toDto(person);
        });

        // A Bénévole sans compte has no Keycloak account to mirror to (ADR 0012)
        if (changed.isWithoutAccount()) return changed;
        try {
            keycloakRoleMirror.setRoleGroup(personId, role);
        } catch (RuntimeException e) {
            // ADR 0002: the database Role still applies; the group is repaired on the next Role change
            Log.warnf(e, "Could not mirror Role %s of %s to Keycloak", role.id(), personId);
        }
        return changed;
    }

    /**
     * The person's Secteur once their Role changes (ADR 0004). Below the Super admin, the actor acts only on
     * Bénévoles and on their own Secteur's people, and gives their own Secteur. The Super admin names it when
     * appointing an Admin or giving a first Secteur. A Bénévole belongs to none.
     */
    private SectorEntity sectorAfter(UserEntity actor, UserEntity person, RoleEnum role, UUID sectorId) {
        if (actor.getRole() != RoleEnum.SUPER_ADMIN) {
            if (sectorId != null)
                throw new ForbiddenRoleChangeException("Only the Super admin names a Secteur.");
            if (actor.getSector() == null)
                throw new ForbiddenRoleChangeException(actor.getUserId() + " belongs to no Secteur.");
            if (person.getSector() != null && !person.belongsTo(actor.getSector()))
                throw new ForbiddenRoleChangeException(person.getUserId() + " belongs to another Secteur.");
            // A Bénévole of the pool joins a Secteur they rode with
            if (person.getSector() == null && role.isAtLeast(RoleEnum.MEMBRE)
                    && !userRepository.rodeWith(person.getUserId(), actor.getSector().getSectorId()))
                throw new ForbiddenRoleChangeException(person.getUserId() + " has not ridden with your Secteur.");
            return role.isAtLeast(RoleEnum.MEMBRE) ? actor.getSector() : null;
        }
        if (!role.isAtLeast(RoleEnum.MEMBRE)) return null;
        boolean namesSector = role == RoleEnum.ADMIN || person.getSector() == null;
        if (!namesSector) {
            if (sectorId != null && !sectorId.equals(person.getSector().getSectorId()))
                throw new InvalidResourceException("Only appointing an Admin changes a person's Secteur.");
            return person.getSector();
        }
        if (sectorId == null)
            throw new InvalidResourceException("Name the Secteur of " + person.getUserId() + ".");
        SectorEntity sector = internalSectorService.doGetEntityById(sectorId)
                .orElseThrow(() -> new UnknownResourceException("Sector not found: " + sectorId));
        if (sector.isClosed())
            throw new InvalidResourceException("A Secteur fermé gets nobody: " + sectorId);
        return sector;
    }

    @Override
    @Transactional
    public UserDto updateProfile(UUID personId, ProfileDto profile) {
        UserEntity person = userRepository.findByIdOptional(personId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
        applyCoordonnees(person, profile);
        return userMapper.toDto(person);
    }

    @Override
    @Transactional
    public UserDto updateCoordonnees(UUID actorId, UUID personId, ProfileDto coordonnees) {
        UserEntity actor = userRepository.findByIdOptional(actorId)
                .orElseThrow(() -> new ForbiddenActionException("Unknown actor: " + actorId));
        UserEntity person = userRepository.findByIdOptional(personId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
        if (!person.isWithoutAccount())
            throw new ForbiddenActionException(personId + " keeps their own details in Mon profil.");
        if (actor.getRole() != RoleEnum.SUPER_ADMIN && !isInscritOf(person, actor.getSector()))
            throw new ForbiddenActionException(personId + " is not an Inscrit of your Secteur.");
        applyCoordonnees(person, coordonnees);
        return userMapper.toDto(person);
    }

    @Override
    @Transactional
    public UserEntity doCreateWithoutAccount(String firstName, String lastName, LocalDate birthDate, String phone) {
        if (isBlank(firstName) || isBlank(lastName))
            throw new InvalidResourceException("A person has a first and a last name.");
        if (birthDate == null)
            throw new InvalidResourceException("A birth date is required to sign up.");
        UserEntity person = UserEntity.builder()
                .userId(UUID.randomUUID())
                .withoutAccount(true)
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .role(RoleEnum.BENEVOLE)
                .build();
        applyCoordonnees(person, new ProfileDto(phone, birthDate));
        userRepository.persist(person);
        return person;
    }

    /** The phone number (blank: none) and the birth date (left out: kept), between 1900 and today. */
    private static void applyCoordonnees(UserEntity person, ProfileDto coordonnees) {
        person.setPhone(isBlank(coordonnees.phone()) ? null : coordonnees.phone().trim());
        if (coordonnees.birthDate() != null) {
            if (coordonnees.birthDate().isAfter(LocalDate.now()) || coordonnees.birthDate().isBefore(LocalDate.of(1900, 1, 1)))
                throw new InvalidResourceException("A birth date lies between 1900 and today: " + coordonnees.birthDate());
            person.setBirthDate(coordonnees.birthDate());
        }
    }

    @Override
    @Transactional
    public UserDto rename(UUID actorId, UUID personId, NamesDto names) {
        if (actorId.equals(personId))
            throw new ForbiddenActionException("Nobody renames themselves.");
        if (isBlank(names.firstName()) || isBlank(names.lastName()))
            throw new InvalidResourceException("A person has a first and a last name.");
        UserEntity actor = userRepository.findByIdOptional(actorId)
                .orElseThrow(() -> new ForbiddenActionException("Unknown actor: " + actorId));
        UserEntity person = userRepository.findByIdOptional(personId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
        if (actor.getRole() != RoleEnum.SUPER_ADMIN && !isInscritOf(person, actor.getSector()))
            throw new ForbiddenActionException(personId + " is not an Inscrit of the Admin's Secteur.");

        person.setFirstName(names.firstName().trim());
        person.setLastName(names.lastName().trim());
        return userMapper.toDto(person);
    }

    /** A Secteur's Inscrits: its people, and the Bénévoles who signed up for one of its Events (ADR 0004). */
    private boolean isInscritOf(UserEntity person, SectorEntity sector) {
        if (sector == null) return false;
        return person.belongsTo(sector)
                || (person.getRole() == RoleEnum.BENEVOLE && userRepository.rodeWith(person.getUserId(), sector.getSectorId()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    @Transactional
    public UserDto setBureauFunction(UUID actorId, UUID personId, BureauFunctionEnum function) {
        if (actorId.equals(personId))
            throw new ForbiddenActionException("Nobody sets their own Fonction au Bureau.");
        UserEntity person = userRepository.findByIdOptional(personId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
        if (person.getRole() != RoleEnum.BUREAU && person.getRole() != RoleEnum.ADMIN)
            throw new InvalidResourceException("Only the Bureau and the Admins sit on a Bureau: " + personId);
        if (function == null && person.getRole() == RoleEnum.BUREAU)
            throw new InvalidResourceException("The Bureau Role sits on the Bureau: " + personId);
        if (!viewer.scope().covers(person.getSector()))
            throw new ForbiddenActionException(personId + " is not of your Secteur.");

        // One Président per Secteur (ADR 0004): the previous one stays on the Bureau
        if (function == BureauFunctionEnum.PRESIDENT)
            userRepository.findPresidentsOf(person.getSector().getSectorId()).stream()
                    .filter(previous -> !previous.getUserId().equals(personId))
                    .forEach(previous -> previous.setBureauFunction(BureauFunctionEnum.MEMBRE_DU_BUREAU));
        person.setBureauFunction(function);
        return userMapper.toDto(person);
    }

    @Override
    @Transactional
    public UserDto doCreate(UserDto userDto) {
        if (userRepository.existsById(userDto.getUserId()))
                throw new DuplicateResourceException("This resource already exists in the system.");

        UserEntity userEntity = userMapper.toEntity(userDto);
        userEntity.setRole(RoleEnum.BENEVOLE);
        userRepository.persist(userEntity);

        return userMapper.toDto(userEntity);
    }

    @Override
    @Transactional
    public UserDto doUpdate(UserDto userDto) {
        UserEntity userEntity = userRepository.findByIdOptional(userDto.getUserId())
                .orElseThrow(() -> new UnknownResourceException("This resource is unknown in the system and cannot be updated."));

        // Names belong to the database once set (ADR 0007): Keycloak only fills those still missing
        if (isBlank(userEntity.getFirstName()) && !isBlank(userDto.getFirstName()))
            userEntity.setFirstName(userDto.getFirstName().trim());
        if (isBlank(userEntity.getLastName()) && !isBlank(userDto.getLastName()))
            userEntity.setLastName(userDto.getLastName().trim());
        return userMapper.toDto(userEntity);
    }


    //Todo: il va falloir par principe permettre la suppression d'un user. Nous devons permettre à chacun de supprimer ses traces.
    @Override
    @Transactional
    public void doDelete(UUID userId) {
        // On peut cependant imaginer tester si le user est utilisé dans une autre table et le supprimer dans le cas contraire
        //userRepository.deleteById(userId);
        throw new NotImplementedYetException(this.getClass().getSimpleName());
    }

    @Override
    public Optional<UserEntity> doGetEntityById(UUID userId) {
        return userRepository.findByIdOptional(userId);
    }

}
