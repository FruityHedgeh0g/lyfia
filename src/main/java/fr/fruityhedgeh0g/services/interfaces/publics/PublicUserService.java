package fr.fruityhedgeh0g.services.interfaces.publics;

import fr.fruityhedgeh0g.dtos.userDtos.NamesDto;
import fr.fruityhedgeh0g.dtos.userDtos.ProfileDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicUserService {
    List<UserDto> listAll();
    UserDto getById(@NotNull UUID userId);

    /**
     * The logged-in person. Someone registered in Keycloak before the application was deployed never produced
     * a "user created" event: they join as a Bénévole, with the names from their token. The e-mail is copied from the
     * token at each sign-in: it is their Keycloak account's.
     */
    UserDto getOrJoin(@NotNull UUID personId, String firstName, String lastName, String email);

    /** Promotion chain: see {@link fr.fruityhedgeh0g.enums.RoleEnum#maySetRole}. The database Role applies even if the Keycloak mirror fails. */
    /**
     * Promotion chain, within the actor's Secteur (ADR 0004): a promoted Bénévole joins the actor's Secteur,
     * a demoted one leaves it. The Super admin names the Secteur ({@code sectorId}) when appointing an Admin,
     * the only case where a person changes Secteur, or when giving someone their first Secteur.
     */
    UserDto changeRole(@NotNull UUID actorId, @NotNull UUID personId, @NotNull RoleEnum role, UUID sectorId);

    /** A person edits their own phone number; their names are an Admin's (ADR 0007). */
    UserDto updateProfile(@NotNull UUID personId, @NotNull ProfileDto profile);

    /**
     * An Admin corrects the names of one of their Secteur's Inscrits; the Super admin, anyone's.
     * Nobody renames themselves (ADR 0007).
     */
    UserDto rename(@NotNull UUID actorId, @NotNull UUID personId, @NotNull NamesDto names);

    /**
     * Sets the Fonction au Bureau of a Bureau member or an Admin of the actor's Secteur (ADR 0001): a Président
     * makes the previous one Membre du Bureau; none takes an Admin off the Bureau, never someone with the Bureau
     * Role. Nobody sets their own.
     */
    UserDto setBureauFunction(@NotNull UUID actorId, @NotNull UUID personId, BureauFunctionEnum function);



//    Try<UserDto> getUserById(@NotNull UUID userId);
//    Try<List<UserDto>> getAllUsers();
//    Try<UserDto> createUser(@NotNull @Valid UserDto userDto);
//    Try<UserDto> updateUser(@NotNull @Valid UserDto userDto);
//    Try<Boolean> internalExistsById(@NotNull UUID userId);
//    Try<UserEntity> internalGetUserById(@NotNull UUID userId);
}
