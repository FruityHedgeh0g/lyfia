package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.dtos.mediaDtos.MediaAltDto;
import fr.fruityhedgeh0g.dtos.mediaDtos.MediaDto;
import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.enums.MediaVariantEnum;
import fr.fruityhedgeh0g.services.GalleryService;
import fr.fruityhedgeh0g.storage.StoredFile;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * The medias: those outside the Galerie listed to everyone (the public site shows them), uploaded and described by
 * the Bureau; every Media's files are served here (ADR 0014), a Photo de la Galerie's as its Visibilité allows.
 */
@Path("/medias")
public class MediaController {

    @Inject
    PublicMediaService mediaService;

    @Inject
    MediaFileService mediaFileService;

    @Inject
    FeatureLevers featureLevers;

    @Inject
    GalleryService galleryService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Basic.class) List<MediaDto> getAllMedias(){
        return mediaService.listAll();
    }

    /**
     * A media's file: the variant asked for ({@code ?variante=vignette|affichage|original}, its original when it has
     * none). A file never changes, so browsers keep it for a year; a Photo de la Galerie not Publique is served only
     * to whom its Visibilité allows, and kept by their browser only (ADR 0014).
     */
    @GET
    @Path("/{mediaId}/content")
    public Response getContent(@PathParam("mediaId") UUID mediaId, @QueryParam("variante") String variante){
        MediaEntity media = mediaFileService.find(mediaId).orElseThrow(NotFoundException::new);
        GalleryService.Access access = galleryService.accessTo(media);
        if (!access.allowed()) throw new NotFoundException();
        StoredFile file = mediaFileService.file(media, MediaVariantEnum.of(variante)).orElseThrow(NotFoundException::new);
        return Response.ok(file.content(), file.mimeType())
                .header("Cache-Control", access.isPublic() ? "public, max-age=31536000, immutable" : "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    /** An image used outside the Galerie: a Post's banner, the carousel, the logo. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Basic.class) MediaDto upload(@RestForm("file") FileUpload file, @RestForm("alt") String alt) throws IOException {
        featureLevers.require(FeatureEnum.DEPOT_MEDIAS);
        if (file == null) throw new BadRequestException("An upload carries a file.");
        if (file.size() > MediaFileService.MAX_SIZE) throw new BadRequestException("An image weighs at most 8 MB.");
        try (InputStream content = Files.newInputStream(file.uploadedFile())) {
            return mediaFileService.upload(new MediaFileService.Upload(file.fileName(), file.contentType(), file.size(), content), alt);
        }
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
