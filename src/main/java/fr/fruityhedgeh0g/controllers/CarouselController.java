package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.dtos.CarouselItemDto;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.services.CarouselService;
import fr.fruityhedgeh0g.services.FeatureLevers;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * The home page's carousel: Visiteurs see its active slides; the Bureau sees and manages them all, even while
 * Carrousel is off (ADR 0009).
 */
@Path("/carousel")
@Produces(MediaType.APPLICATION_JSON)
public class CarouselController {

    @Inject CarouselService carouselService;
    @Inject SecurityIdentity identity;
    @Inject FeatureLevers featureLevers;

    @GET
    public List<CarouselItemDto> list(){
        featureLevers.requireForPublic(FeatureEnum.CARROUSEL);
        return carouselService.list(identity.hasRole("bureau"));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public CarouselItemDto create(@NotNull CarouselItemDto.Input input){
        return carouselService.create(input);
    }

    @PUT
    @Path("/{itemId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public CarouselItemDto update(@PathParam("itemId") UUID itemId, @NotNull CarouselItemDto.Input input){
        return carouselService.update(itemId, input);
    }

    @DELETE
    @Path("/{itemId}")
    @RolesAllowed("bureau")
    public void delete(@PathParam("itemId") UUID itemId){
        carouselService.delete(itemId);
    }

    @POST
    @Path("/{itemId}/move/{direction: up|down}")
    @RolesAllowed("bureau")
    public List<CarouselItemDto> move(@PathParam("itemId") UUID itemId, @PathParam("direction") String direction){
        return carouselService.move(itemId, "up".equals(direction));
    }
}
