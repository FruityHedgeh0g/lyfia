package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.dtos.dechargeDtos.DechargeDocumentDto;
import fr.fruityhedgeh0g.entities.DechargeDocumentEntity;
import fr.fruityhedgeh0g.services.DechargeService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/** The décharge de responsabilité for minors: the version in force, any version's PDF, and a new version from the Super admin. */
@Path("/decharge")
public class DechargeController {

    @Inject
    DechargeService dechargeService;

    @Inject
    JsonWebToken token;

    /** The version in force; 204 while none has been uploaded. */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public Response current(){
        return dechargeService.current().map(d -> Response.ok(d).build()).orElseGet(() -> Response.noContent().build());
    }

    @GET
    @Path("/{documentId}/content")
    @Produces(DechargeDocumentEntity.MIME_TYPE)
    @Authenticated
    public Response content(@PathParam("documentId") UUID documentId){
        return dechargeService.content(documentId)
                .map(content -> Response.ok(content, DechargeDocumentEntity.MIME_TYPE)
                        .header("Content-Disposition", "inline; filename=\"decharge.pdf\"")
                        .header("X-Content-Type-Options", "nosniff")
                        .build())
                .orElseThrow(NotFoundException::new);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("super_admin")
    public DechargeDocumentDto upload(@RestForm("file") FileUpload file) throws IOException {
        if (file == null) throw new BadRequestException("An upload carries a file.");
        if (file.size() > DechargeDocumentEntity.MAX_SIZE) throw new BadRequestException("The décharge weighs at most 8 MB.");
        return dechargeService.upload(file.fileName(), file.contentType(), Files.readAllBytes(file.uploadedFile()),
                UUID.fromString(token.getSubject()));
    }
}
