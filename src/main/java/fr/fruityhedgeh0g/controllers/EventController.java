package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.dtos.eventDtos.EventStatusChangeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.GroupMaximumDto;
import fr.fruityhedgeh0g.dtos.eventDtos.MonGroupeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RegistrationDto;
import fr.fruityhedgeh0g.dtos.eventDtos.DemandeRequestDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterExportDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.services.interfaces.publics.PublicEventService;
import fr.fruityhedgeh0g.utilities.export.RosterSpreadsheet;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

/** Reading Events is open to anonymous Visiteurs (see quarkus.http.auth.permission.public-events); Events are never deleted. */
@Path("/events")
public class EventController {

    @Inject
    PublicEventService eventService;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken token;

    private UUID me() {
        return UUID.fromString(token.getSubject());
    }

    /** The person making the request, or none for a Visiteur. */
    private UUID meOrNone() {
        return token.getSubject() == null ? null : UUID.fromString(token.getSubject());
    }

    private boolean seesPlanification() {
        return identity.hasRole("bureau");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Basic.class) List<EventDto> getAllEvents(){
        return eventService.listAll(seesPlanification(), meOrNone());
    }

    /** The current person's sign-ups ("Mes événements"). */
    @GET
    @Path("/registrations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("benevole")
    public List<RegistrationDto> getMyRegistrations(){
        return eventService.registrationsOf(me());
    }

    /**
     * As pilote, optional {@code ?groupId=} creates a Demande de groupe for that Groupe;
     * {@code ?piloteId=} signs up as passager of that pilote, who chooses the Groupe.
     */
    @PUT
    @Path("/{eventId}/registration")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("benevole")
    public RegistrationDto signUp(@PathParam("eventId") UUID eventId, @QueryParam("groupId") UUID groupId,
                                  @QueryParam("piloteId") UUID piloteId){
        if (piloteId == null) return eventService.signUp(eventId, me(), groupId);
        if (groupId != null) throw new BadRequestException("A passager rides with their pilote's Groupe.");
        return eventService.signUpAsPassager(eventId, me(), piloteId);
    }

    /** The pilotes signed up for the Event, for a passager to choose from (names only). */
    @GET
    @Path("/{eventId}/pilotes")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("benevole")
    public List<NestedUserDto> getPilotes(@PathParam("eventId") UUID eventId){
        return eventService.pilotesOf(eventId);
    }

    @PUT
    @Path("/{eventId}/registration/demande")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("benevole")
    public RegistrationDto requestGroup(@PathParam("eventId") UUID eventId, @Valid @NotNull DemandeRequestDto demande){
        if (demande.groupId() == null) throw new BadRequestException("A Demande de groupe names a Groupe.");
        return eventService.requestGroup(eventId, me(), demande.groupId());
    }

    /** {decision}: accept or refuse; the asked Groupe's Chef or the Bureau. */
    @POST
    @Path("/{eventId}/demandes/{personId}/{decision: accept|refuse}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("chef_de_groupe")
    public RegistrationDto decideDemande(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                         @PathParam("decision") String decision){
        return eventService.decideDemande(eventId, personId, actor(), "accept".equals(decision));
    }

    @PUT
    @Path("/{eventId}/roster/{personId}/group/{groupId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto placeInGroup(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                  @PathParam("groupId") UUID groupId){
        return eventService.placeInGroup(eventId, personId, groupId);
    }

    /** A Groupe's maximum at this Event; null or 0 removes it. */
    @PUT
    @Path("/{eventId}/groups/{groupId}/maximum")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto setGroupMaximum(@PathParam("eventId") UUID eventId, @PathParam("groupId") UUID groupId,
                                     @Valid @NotNull GroupMaximumDto maximum){
        return eventService.setGroupMaximum(eventId, groupId, maximum.maximum());
    }

    @DELETE
    @Path("/{eventId}/roster/{personId}/group")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("chef_de_groupe")
    public RegistrationDto takeOutOfGroup(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId){
        return eventService.takeOutOfGroup(eventId, personId, actor());
    }

    /** Mon groupe: per Event, the riders and pending Demandes of the Groupe the Chef leads. */
    @GET
    @Path("/mon-groupe")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("chef_de_groupe")
    public MonGroupeDto getMonGroupe(){
        return eventService.monGroupe(me());
    }

    private PublicEventService.Actor actor() {
        return new PublicEventService.Actor(me(), identity.hasRole("bureau"));
    }

    @DELETE
    @Path("/{eventId}/registration")
    @RolesAllowed("benevole")
    public void withdraw(@PathParam("eventId") UUID eventId){
        eventService.withdraw(eventId, me());
    }

    @GET
    @Path("/{eventId}/roster")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto getRoster(@PathParam("eventId") UUID eventId){
        return eventService.roster(eventId);
    }

    /** The roster as an .xlsx download: one tab per Groupe, one without a Groupe, one for the Liste d'attente. */
    @GET
    @Path("/{eventId}/roster/export")
    @Produces(RosterSpreadsheet.MEDIA_TYPE)
    @RolesAllowed("bureau")
    public Response exportRoster(@PathParam("eventId") UUID eventId){
        RosterExportDto export = eventService.exportRoster(eventId);
        return Response.ok(export.content())
                .header("Content-Disposition", "attachment; filename=\"" + export.fileName() + "\"")
                .build();
    }

    @POST
    @Path("/{eventId}/roster/{personId}/promote")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto promote(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId){
        return eventService.promote(eventId, personId);
    }

    @DELETE
    @Path("/{eventId}/roster/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto removeFromRoster(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId){
        return eventService.removeFromRoster(eventId, personId);
    }

    @GET
    @Path("/{eventId}")
    @Produces(MediaType.APPLICATION_JSON)
    public @JsonView(Views.Detailed.class) EventDto getEvent(@PathParam("eventId") UUID eventId){
        return eventService.getById(eventId, seesPlanification(), meOrNone());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.CreationResponse.class) EventDto addEvent(@JsonView(Views.Creation.class) EventDto eventDto){
        return eventService.create(eventDto);
    }

    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) EventDto updateEvent(@JsonView(Views.Update.class) EventDto eventDto){
        return eventService.update(eventDto);
    }

    @PUT
    @Path("/{eventId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public @JsonView(Views.Detailed.class) EventDto changeStatus(@PathParam("eventId") UUID eventId, @Valid @NotNull EventStatusChangeDto change){
        return eventService.changeStatus(eventId, change.status());
    }
}
