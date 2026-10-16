package fr.fruityhedgeh0g.services.decorators.logs;

import fr.fruityhedgeh0g.dtos.userDtos.NamesDto;
import fr.fruityhedgeh0g.dtos.userDtos.ProfileDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.ForbiddenRoleChangeException;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.services.interfaces.UserService;
import io.quarkus.logging.Log;
import io.vavr.control.Try;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Priority(200)
@Decorator
public class UserLogDecorator implements UserService{
    @Inject
    @Delegate
    UserService userService;

    @Override
    public List<UserDto> listAll() {
        Log.debugf("Retrieving all users...");
        return Try.of(userService::listAll)
                .onSuccess(users -> Log.debugf("%d users retrieved.",users.size()))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving users."))
                .get();
    }

    @Override
    public UserDto getById(UUID userId) {
        Log.debugf("Retrieving user by id %s...",userId);
        return Try.of(() -> userService.getById(userId))
                .onSuccess(user -> {
                        Log.debugf("User retrieved: "+user.toString());
                })
                .onFailure(t -> {
                    switch(t){
                        case UnknownResourceException ex -> Log.errorf(ex,"User with id %s not found.", userId);
                        default -> Log.errorf(t,"An error occurred while retrieving user.");
                    }
                })
                .get();
    }

    @Override
    public UserDto getOrJoin(UUID personId, String firstName, String lastName, String email) {
        Log.debugf("Retrieving the logged-in person %s...", personId);
        return Try.of(() -> userService.getOrJoin(personId, firstName, lastName, email))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving the logged-in person %s.", personId))
                .get();
    }

    @Override
    public UserDto changeRole(UUID actorId, UUID personId, RoleEnum role, UUID sectorId) {
        Log.debugf("User %s sets the Role of %s to %s...", actorId, personId, role.id());
        return Try.of(() -> userService.changeRole(actorId, personId, role, sectorId))
                .onSuccess(user -> Log.infof("Role of %s set to %s by %s.", personId, role.id(), actorId))
                .onFailure(t -> {
                    switch(t){
                        case ForbiddenRoleChangeException ex -> Log.warnf("Role change refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", personId);
                        default -> Log.errorf(t,"An error occurred while changing a Role.");
                    }
                })
                .get();
    }

    @Override
    public UserDto updateProfile(UUID personId, ProfileDto profile) {
        Log.debugf("%s updates their profile...", personId);
        return Try.of(() -> userService.updateProfile(personId, profile))
                .onSuccess(user -> Log.debugf("Profile of %s updated.", personId))
                .onFailure(t -> {
                    switch(t){
                        case InvalidResourceException ex -> Log.warnf("Profile refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", personId);
                        default -> Log.errorf(t,"An error occurred while updating a profile.");
                    }
                })
                .get();
    }

    @Override
    public UserDto rename(UUID actorId, UUID personId, NamesDto names) {
        Log.debugf("%s renames %s...", actorId, personId);
        return Try.of(() -> userService.rename(actorId, personId, names))
                .onSuccess(user -> Log.infof("%s renamed by %s.", personId, actorId))
                .onFailure(t -> {
                    switch(t){
                        case ForbiddenActionException ex -> Log.warnf("Rename refused: %s", ex.getMessage());
                        case InvalidResourceException ex -> Log.warnf("Rename refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", personId);
                        default -> Log.errorf(t,"An error occurred while renaming a person.");
                    }
                })
                .get();
    }

    @Override
    public UserDto updateCoordonnees(UUID actorId, UUID personId, ProfileDto coordonnees) {
        Log.debugf("%s updates the details of %s...", actorId, personId);
        return Try.of(() -> userService.updateCoordonnees(actorId, personId, coordonnees))
                .onSuccess(user -> Log.infof("Details of %s updated by %s.", personId, actorId))
                .onFailure(t -> {
                    switch(t){
                        case ForbiddenActionException ex -> Log.warnf("Details update refused: %s", ex.getMessage());
                        case InvalidResourceException ex -> Log.warnf("Details update refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", personId);
                        default -> Log.errorf(t,"An error occurred while updating a person's details.");
                    }
                })
                .get();
    }

    @Override
    public UserEntity doCreateWithoutAccount(String firstName, String lastName, LocalDate birthDate, String phone) {
        Log.debugf("[INTERNAL] Recording a Bénévole sans compte...");
        return Try.of(() -> userService.doCreateWithoutAccount(firstName, lastName, birthDate, phone))
                .onSuccess(user -> Log.infof("Bénévole sans compte %s recorded.", user.getUserId()))
                .onFailure(t -> Log.warnf("Bénévole sans compte refused: %s", t.getMessage()))
                .get();
    }

    @Override
    public UserDto setBureauFunction(UUID actorId, UUID personId, BureauFunctionEnum function) {
        Log.debugf("%s sets the Fonction au Bureau of %s to %s...", actorId, personId, function);
        return Try.of(() -> userService.setBureauFunction(actorId, personId, function))
                .onSuccess(user -> Log.infof("%s's Fonction au Bureau is now %s.", personId, function))
                .onFailure(t -> {
                    switch(t){
                        case ForbiddenActionException ex -> Log.warnf("Fonction au Bureau refused: %s", ex.getMessage());
                        case InvalidResourceException ex -> Log.warnf("Fonction au Bureau refused: %s", ex.getMessage());
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", personId);
                        default -> Log.errorf(t,"An error occurred while setting a Fonction au Bureau.");
                    }
                })
                .get();
    }

    @Override
    public UserDto doCreate(UserDto userDto) {
        Log.debugf("Creating new user: %s", userDto.toString());
        return Try.of(() -> userService.doCreate(userDto))
                .onSuccess(user -> Log.debugf("User created."))
                .onFailure(t -> {
                    switch(t){
                        case DuplicateResourceException ex -> Log.errorf(ex,"User %s already existing.", userDto.getUserId());
                        default -> Log.errorf(t,"An error occurred while creating user.");
                    }
                })
                .get();
    }

    @Override
    public UserDto doUpdate(UserDto userDto) {
        Log.debugf("Updating an existing user: %s", userDto.toString());
        return Try.of(() -> userService.doUpdate(userDto))
                .onSuccess(user -> Log.debugf("User updated."))
                .onFailure(t -> {
                    switch(t){
                        case UnknownResourceException ex -> Log.errorf(ex,"User %s not found.", userDto.getUserId());
                        default -> Log.errorf(t,"An error occurred while updating user.");
                    }
                })
                .get();
    }

    @Override
    public void doDelete(UUID userId) {

        Log.debugf("Deleting user by id %s...",userId);
        Try.run(() -> userService.doDelete(userId))
                .onSuccess(v -> Log.debugf("User deleted."))
                .onFailure(t -> Log.errorf(t,"An error occurred during user deletion."))
                .get();
    }

    @Override
    public Optional<UserEntity> doGetEntityById(UUID userId) {
        Log.debugf("[INTERNAL] Retrieve user by id %s...",userId);
        return Try.of(() -> userService.doGetEntityById(userId))
                .onSuccess(user -> {
                    if (user.isPresent())
                        Log.debugf("User retrieved.");
                    else Log.debugf("User %s not found.",userId);
                })
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving user."))
                .get();
    }
}
