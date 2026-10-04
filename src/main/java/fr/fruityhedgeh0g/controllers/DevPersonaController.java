package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DevPersonaAuthentication;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;

/**
 * Development only, built into dev and test builds alone: try the site as any Role, without Keycloak. Each persona
 * is a person of its own in the database (one per Role and Secteur), created on first use; choosing one sets a
 * cookie with which every request acts as that person (DevPersonaAuthentication), so whatever the Role may do can
 * really be done. A production build has no such endpoint (404), so the site offers no persona there.
 */
@Path("/dev/persona")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile(anyOf = {"dev", "test"})
public class DevPersonaController {

    /** A persona: a Role and, from Membre to Admin, a Secteur (the first open one when none is given). */
    public record Persona(RoleEnum role, UUID sectorId) {}

    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;

    /** The persona in use, or no content when none. */
    @GET
    public Persona current(@CookieParam(DevPersonaAuthentication.COOKIE) String personId) {
        if (personId == null || personId.isBlank()) return null;
        try {
            return userRepository.findByIdOptional(UUID.fromString(personId))
                    .map(person -> new Persona(person.getRole(), person.getSector() == null ? null : person.getSector().getSectorId()))
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response become(@NotNull Persona persona) {
        if (persona.role() == null || persona.role() == RoleEnum.VISITEUR)
            throw new BadRequestException("A persona is a registered Role: from benevole to super_admin; a Visiteur has none.");
        boolean hasSecteur = persona.role().isAtLeast(RoleEnum.MEMBRE) && persona.role() != RoleEnum.SUPER_ADMIN;
        SectorEntity sector = hasSecteur ? sectorOf(persona.sectorId()) : null;

        UUID personId = UUID.nameUUIDFromBytes(("lyfia-dev-persona:" + persona.role().id() + ":"
                + (sector == null ? "" : sector.getSectorId())).getBytes(StandardCharsets.UTF_8));
        UserEntity person = userRepository.findByIdOptional(personId).orElseGet(() -> {
            UserEntity created = UserEntity.builder().userId(personId).firstName("Persona").lastName("").build();
            userRepository.persist(created);
            return created;
        });
        person.setFirstName("Persona");
        person.setLastName(label(persona.role()) + (sector == null ? "" : " " + sector.getName()));
        person.setPhone("06 00 00 00 00");
        person.setRole(persona.role());
        person.setSector(sector);
        Log.warnf("Dev persona in use: %s%s (%s)", persona.role().id(), sector == null ? "" : " of " + sector.getName(), personId);
        return Response.ok(new Persona(persona.role(), sector == null ? null : sector.getSectorId()))
                .cookie(cookie(personId.toString(), NewCookie.DEFAULT_MAX_AGE))
                .build();
    }

    /** Back to no persona: a Visiteur. */
    @DELETE
    public Response leave() {
        return Response.noContent().cookie(cookie("", 0)).build();
    }

    private static NewCookie cookie(String value, int maxAge) {
        return new NewCookie.Builder(DevPersonaAuthentication.COOKIE).value(value).path("/").maxAge(maxAge)
                .httpOnly(true).sameSite(NewCookie.SameSite.LAX).build();
    }

    private static String label(RoleEnum role) {
        String id = role.id().replace('_', ' ');
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private SectorEntity sectorOf(UUID sectorId) {
        if (sectorId != null) {
            SectorEntity sector = sectorRepository.findByIdOptional(sectorId)
                    .orElseThrow(() -> new NotFoundException("Unknown Secteur: " + sectorId));
            if (sector.isClosed()) throw new BadRequestException("A Secteur fermé takes no one.");
            return sector;
        }
        return sectorRepository.listAll().stream()
                .filter(s -> !s.isClosed())
                .min(Comparator.comparing(SectorEntity::getName))
                .orElseThrow(() -> new BadRequestException("No open Secteur: open one first, as the Super admin persona."));
    }
}
