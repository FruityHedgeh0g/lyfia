package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.dtos.mediaDtos.MediaAltDto;
import fr.fruityhedgeh0g.dtos.mediaDtos.MediaDto;
import fr.fruityhedgeh0g.entities.medias.MediaContentEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.services.FeatureLevers;
import fr.fruityhedgeh0g.services.MediaFileService;
import fr.fruityhedgeh0g.services.interfaces.MediaService;
import fr.fruityhedgeh0g.services.interfaces.publics.PublicMediaService;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * The medias: listed and served to everyone (the public site shows them), uploaded and described by the Bureau.
 * Their files live in the database (ADR 0008).
 */
@Path("/medias")
public class MediaController {

    @Inject
    PublicMediaService mediaService;

    @Inject
    MediaFileService mediaFileService;

    @Inject
    FeatureLevers featureLevers;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Basic.class) List<MediaDto> getAllMedias(){
        return mediaService.listAll();
    }

    /** The public gallery: the same medias, refused while Galerie photos is off, except to the Bureau who prepares them. */
    @GET
    @Path("/gallery")
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Basic.class) List<MediaDto> getGallery(){
        featureLevers.requireForPublic(FeatureEnum.GALERIE_PHOTOS);
        return mediaService.listAll();
    }

    /** A media's file never changes: browsers keep it for a year. */
    @GET
    @Path("/{mediaId}/content")
    public Response getContent(@PathParam("mediaId") UUID mediaId){
        return mediaFileService.file(mediaId)
                .map(file -> Response.ok(file.content(), file.mimeType())
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .header("X-Content-Type-Options", "nosniff")
                        .build())
                .orElseThrow(NotFoundException::new);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Basic.class) MediaDto upload(@RestForm("file") FileUpload file, @RestForm("alt") String alt) throws IOException {
        if (file == null) throw new BadRequestException("An upload carries a file.");
        if (file.size() > MediaContentEntity.MAX_SIZE) throw new BadRequestException("An image weighs at most 8 MB.");
        return mediaFileService.upload(file.fileName(), file.contentType(), Files.readAllBytes(file.uploadedFile()), alt);
    }

    @PATCH
    @Path("/{mediaId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Basic.class) MediaDto describe(@PathParam("mediaId") UUID mediaId, @NotNull MediaAltDto description){
        return mediaFileService.describe(mediaId, description.alt());
    }
}
