package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;

import fr.fruityhedgeh0g.dtos.userDtos.BureauFunctionDto;
import fr.fruityhedgeh0g.dtos.userDtos.NamesDto;
import fr.fruityhedgeh0g.dtos.userDtos.ProfileDto;
import fr.fruityhedgeh0g.dtos.userDtos.RoleChangeDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.services.interfaces.UserService;
import fr.fruityhedgeh0g.services.interfaces.publics.PublicUserService;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/users")
public class UserController {
    @Inject
    JsonWebToken token;

    @Inject
    PublicUserService userService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Basic.class) List<UserDto> getAllUsers(){
        return userService.listAll();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/me")
    @Authenticated
    public @JsonView(Views.Detailed.class) UserDto getCurrentUser(){
        UUID userId = UUID.fromString(token.getSubject());
        return userService.getOrJoin(userId, token.getClaim("given_name"), token.getClaim("family_name"));
    }

    @PATCH
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public @JsonView(Views.Detailed.class) UserDto updateMyProfile(@NotNull ProfileDto profile){
        return userService.updateProfile(UUID.fromString(token.getSubject()), profile);
    }

    @PATCH
    @Path("/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public @JsonView(Views.Detailed.class) UserDto rename(@PathParam("userId") UUID userId, @NotNull NamesDto names){
        return userService.rename(UUID.fromString(token.getSubject()), userId, names);
    }

    @PUT
    @Path("/{userId}/role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) UserDto changeRole(@PathParam("userId") UUID userId, @Valid @NotNull RoleChangeDto change){
        return userService.changeRole(UUID.fromString(token.getSubject()), userId, change.role(), change.sectorId());
    }

    @PUT
    @Path("/{userId}/bureau-function")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public @JsonView(Views.Detailed.class) UserDto setBureauFunction(@PathParam("userId") UUID userId, @NotNull BureauFunctionDto change){
        return userService.setBureauFunction(UUID.fromString(token.getSubject()), userId, change.function());
    }
//
//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    @Consumes(MediaType.APPLICATION_JSON)
//    public @JsonView(Views.CreationResponse.class) UserDto addUser(@JsonView(Views.Creation.class) UserDto userDto){
//        return userService.createUser(userDto).get();
//    }
//
//    @PATCH
//    @Produces(MediaType.APPLICATION_JSON)
//    @Consumes(MediaType.APPLICATION_JSON)
//    public @JsonView(Views.UpdateResponse.class) UserDto updateUser(@JsonView(Views.Update.class) UserDto userDto){
//        return userService.updateUser(userDto).get();
//    }
//
//    @GET
//    @Path("/{userId}")
//    @Produces(MediaType.APPLICATION_JSON)
//    public @JsonView(Views.Detailed.class) UserDto getUserById(@PathParam("userId") UUID userId){
//        return userService.getUserById(userId).get();
//    }

}
