package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.dtos.galleryDtos.AlbumDto;
import fr.fruityhedgeh0g.dtos.galleryDtos.GalleryPhotoChangeDto;
import fr.fruityhedgeh0g.dtos.galleryDtos.GalleryPhotoDto;
import fr.fruityhedgeh0g.dtos.galleryDtos.VisibiliteChangeDto;
import fr.fruityhedgeh0g.services.GallerySettings;
import fr.fruityhedgeh0g.services.GalleryService;
import fr.fruityhedgeh0g.services.MediaFileService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * The Galerie (ADR 0013): its Albums and their photos, seen as each photo's Visibilité allows; deposited, described,
 * made visible and deleted by the Bureau of their Secteur. Their files are at /api/medias/{mediaId}/content.
 */
@Path("/galerie")
public class GalleryController {

    @Inject
    GalleryService galleryService;

    /** The Albums the viewer may open: Secteur and Année, latest first. */
    @GET
    @Path("/albums")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AlbumDto> getAlbums(){
        return galleryService.albums();
    }

    /** An Album's photos, narrowed to an Event ({@code ?eventId=}) or a Groupe ({@code ?groupId=}). */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<GalleryPhotoDto> getAlbum(@QueryParam("sectorId") UUID sectorId, @QueryParam("annee") Integer annee,
                                          @QueryParam("eventId") UUID eventId, @QueryParam("groupId") UUID groupId){
        if (sectorId == null || annee == null) throw new BadRequestException("An Album is a Secteur and an Année.");
        return galleryService.album(sectorId, annee, eventId, groupId);
    }

    /** The largest photo kept, which the depositor's browser scales down to (ADR 0014). */
    @GET
    @Path("/limites")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public GallerySettings.Limits getLimits(){
        return galleryService.limits();
    }

    /**
     * One photo, with the variants its depositor's browser made ({@code affichage}, {@code vignette}); a batch is
     * sent photo by photo. {@code sectorId}: for the Super admin; {@code eventId} gives the Année, else {@code annee}.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public GalleryPhotoDto deposit(@RestForm("original") FileUpload original, @RestForm("affichage") FileUpload affichage,
                                   @RestForm("vignette") FileUpload vignette, @RestForm("sectorId") UUID sectorId,
                                   @RestForm("eventId") UUID eventId, @RestForm("annee") Integer annee,
                                   @RestForm("groupId") List<UUID> groupIds, @RestForm("alt") String alt) throws IOException {
        if (original == null || affichage == null || vignette == null)
            throw new BadRequestException("A photo comes with its original, its display size and its thumbnail.");
        try (InputStream o = Files.newInputStream(original.uploadedFile());
             InputStream a = Files.newInputStream(affichage.uploadedFile());
             InputStream v = Files.newInputStream(vignette.uploadedFile())) {
            return galleryService.deposit(new GalleryService.Deposit(sectorId, eventId, annee, groupIds, alt,
                    upload(original, o), upload(affichage, a), upload(vignette, v)));
        }
    }

    private static MediaFileService.Upload upload(FileUpload file, InputStream content) {
        return new MediaFileService.Upload(file.fileName(), file.contentType(), file.size(), content);
    }

    @PATCH
    @Path("/{photoId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public GalleryPhotoDto change(@PathParam("photoId") UUID photoId, @NotNull GalleryPhotoChangeDto change){
        return galleryService.change(photoId, change);
    }

    @PUT
    @Path("/visibilite")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public List<GalleryPhotoDto> setVisibilite(@Valid @NotNull VisibiliteChangeDto change){
        return galleryService.setVisibilite(change.photoIds(), change.visibilite());
    }

    @DELETE
    @Path("/{photoId}")
    @RolesAllowed("bureau")
    public void delete(@PathParam("photoId") UUID photoId){
        galleryService.delete(photoId);
    }
}
