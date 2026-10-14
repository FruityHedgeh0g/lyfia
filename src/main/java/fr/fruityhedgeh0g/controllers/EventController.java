package fr.fruityhedgeh0g.controllers;

import jakarta.ws.rs.DefaultValue;
import java.time.LocalDate;
import fr.fruityhedgeh0g.dtos.eventDtos.PosteChangeDto;
import fr.fruityhedgeh0g.enums.PosteEnum;
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
     * {@code ?poste=} conducteur (by default), passager, chauffeur_camionnette, point_fixe or service;
     * {@code ?journee=} each Journée signed up for (none: every one, "tout l'Événement"); {@code ?groupId=} creates a
     * Demande de groupe for that Groupe; {@code ?conducteur=} the first and last name of the conducteur a passager comes
     * with ("je viens avec"), matched among those signed up, taking their Groupe. Signing up again adds Journées.
     */
    @PUT
    @Path("/{eventId}/registration")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("benevole")
    public RegistrationDto signUp(@PathParam("eventId") UUID eventId, @QueryParam("poste") String poste,
                                  @QueryParam("groupId") UUID groupId, @QueryParam("conducteur") String conducteur,
                                  @QueryParam("journee") List<LocalDate> journees){
        boolean named = conducteur != null && !conducteur.isBlank();
        PosteEnum chosen = poste == null ? (named ? PosteEnum.PASSAGER : PosteEnum.CONDUCTEUR) : PosteEnum.of(poste);
        if (named && chosen != PosteEnum.PASSAGER) throw new BadRequestException("Only a passager names a conducteur.");
        if (named && groupId != null) throw new BadRequestException("A passager rides with their conducteur's Groupe.");
        return eventService.signUp(eventId, me(), new PublicEventService.SignUp(chosen, groupId, named ? conducteur : null, journees));
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
                                  @PathParam("groupId") UUID groupId,
                                  @QueryParam("withPassager") @DefaultValue("true") boolean withPassager){
        return eventService.placeInGroup(eventId, personId, groupId, withPassager);
    }

    /** The Bureau changes someone's Poste at this Event. */
    @PUT
    @Path("/{eventId}/roster/{personId}/poste")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto changePoste(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                 @Valid @NotNull PosteChangeDto change){
        if (change.poste() == null) throw new BadRequestException("A Poste change names the Poste.");
        return eventService.changePoste(eventId, personId, change.poste());
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
    public RegistrationDto takeOutOfGroup(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                          @QueryParam("withPassager") @DefaultValue("true") boolean withPassager){
        return eventService.takeOutOfGroup(eventId, personId, actor(), withPassager);
    }

    /** The Bureau or the passager's Chef pairs a passager with a conducteur of their Groupe for a Journée. */
    @PUT
    @Path("/{eventId}/roster/{personId}/journees/{journee}/conducteur/{conducteurId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("chef_de_groupe")
    public RegistrationDto pair(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                @PathParam("journee") LocalDate journee, @PathParam("conducteurId") UUID conducteurId){
        return eventService.pair(eventId, personId, journee, conducteurId, actor());
    }

    /** The Bureau or the passager's Chef ends their pairing that Journée. */
    @DELETE
    @Path("/{eventId}/roster/{personId}/journees/{journee}/conducteur")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("chef_de_groupe")
    public RegistrationDto unpair(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                  @PathParam("journee") LocalDate journee){
        return eventService.unpair(eventId, personId, journee, actor());
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
    public void withdraw(@PathParam("eventId") UUID eventId, @QueryParam("journee") List<LocalDate> journees,
                         @QueryParam("withPassager") @DefaultValue("true") boolean withPassager){
        eventService.withdraw(eventId, me(), journees, withPassager);
    }

    @GET
    @Path("/{eventId}/roster")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto getRoster(@PathParam("eventId") UUID eventId, @QueryParam("journee") LocalDate journee){
        return eventService.roster(eventId, journee);
    }

    /** The roster as an .xlsx download: one sheet per Journée. */
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
    public RosterDto promote(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                             @QueryParam("journee") List<LocalDate> journees){
        return eventService.promote(eventId, personId, journees);
    }

    @DELETE
    @Path("/{eventId}/roster/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("bureau")
    public RosterDto removeFromRoster(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                     @QueryParam("journee") List<LocalDate> journees,
                                     @QueryParam("withPassager") @DefaultValue("true") boolean withPassager){
        return eventService.removeFromRoster(eventId, personId, journees, withPassager);
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
