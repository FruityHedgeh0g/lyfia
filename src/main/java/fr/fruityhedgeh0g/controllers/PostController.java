package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.dtos.postDtos.PostDto;
import fr.fruityhedgeh0g.dtos.postDtos.PostStatusChangeDto;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.services.FeatureLevers;
import fr.fruityhedgeh0g.services.interfaces.publics.PublicPostService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

/**
 * Reading Posts is open to anonymous Visiteurs (see quarkus.http.auth.permission.public-posts),
 * who only ever see Publié Posts; the Bureau writes, publishes and unpublishes them.
 * While Actualités is off, only the Bureau, who prepares them, reads them (ADR 0009).
 */
@Path("/posts")
public class PostController {

    @Inject
    PublicPostService postService;

    @Inject
    SecurityIdentity identity;

    @Inject
    FeatureLevers featureLevers;

    @Inject
    JsonWebToken token;

    private UUID me() {
        return UUID.fromString(token.getSubject());
    }

    private boolean seesDrafts() {
        return identity.hasRole("bureau");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    // Detailed: the list shows an excerpt of each Post's content
    public @JsonView(Views.Detailed.class) List<PostDto> getAllPosts(@QueryParam("managed") boolean managed){
        featureLevers.requireForPublic(FeatureEnum.ACTUALITES);
        // ?managed=true: the Posts of the Secteurs the Bureau manages, for the Actualités admin screen
        return postService.listAll(seesDrafts(), managed && seesDrafts());
    }

    @GET
    @Path("/{postId}")
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Detailed.class) PostDto getPost(@PathParam("postId") UUID postId){
        featureLevers.requireForPublic(FeatureEnum.ACTUALITES);
        return postService.getById(postId, seesDrafts());
    }

    /** A new Post is Brouillon, with the current person as author. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) PostDto addPost(@JsonView(Views.Creation.class) PostDto postDto){
        return postService.create(postDto, me());
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) PostDto updatePost(@JsonView(Views.Update.class) PostDto postDto){
        return postService.update(postDto);
    }

    /** {@code publie} publishes, {@code brouillon} unpublishes. */
    @PUT
    @Path("/{postId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) PostDto changeStatus(@PathParam("postId") UUID postId, @Valid @NotNull PostStatusChangeDto change){
        return postService.changeStatus(postId, change.status());
    }
}
