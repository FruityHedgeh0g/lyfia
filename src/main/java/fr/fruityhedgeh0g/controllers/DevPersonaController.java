package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Comparator;
import java.util.UUID;

/**
 * Development only, off unless {@code lyfia.dev-personas} is on (the dev and test profiles): the logged-in person
 * takes any Role (and a Secteur for the Roles that have one) to try the site as that persona, without another
 * Keycloak account. It writes the person's own Role
 * in the database, the source of truth (ADR 0002), skipping the promotion rules and the Keycloak mirror. The Super
 * admin appointed by configuration gets the Role back at the next startup (ADR 0006).
 */
@Path("/dev/persona")
@Authenticated
public class DevPersonaController {

    /** A persona: a Role and, from Membre to Admin, a Secteur (the first open one when none is given). */
    public record Persona(RoleEnum role, UUID sectorId) {}

    @Inject JsonWebToken token;
    @Inject UserRepository userRepository;
    @Inject SectorRepository sectorRepository;

    @ConfigProperty(name = "lyfia.dev-personas", defaultValue = "false")
    boolean enabled;

    /** 204 where personas are offered, 404 elsewhere: the site shows its persona choice accordingly. */
    @GET
    public void offered() {
        requireEnabled();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Persona become(@NotNull Persona persona) {
        requireEnabled();
        if (persona.role() == null || persona.role() == RoleEnum.VISITEUR)
            throw new BadRequestException("A persona is a registered Role: from benevole to super_admin.");
        UserEntity me = userRepository.findByIdOptional(UUID.fromString(token.getSubject()))
                .orElseThrow(() -> new NotFoundException("Unknown person: log in once first."));

        boolean hasSecteur = persona.role().isAtLeast(RoleEnum.MEMBRE) && persona.role() != RoleEnum.SUPER_ADMIN;
        SectorEntity sector = hasSecteur ? sectorOf(persona.sectorId()) : null;
        me.setRole(persona.role());
        me.setSector(sector);
        me.setPresident(false);
        Log.warnf("Dev persona: %s is now %s%s", me.getUserId(), persona.role().id(),
                sector == null ? "" : " of " + sector.getName());
        return new Persona(persona.role(), sector == null ? null : sector.getSectorId());
    }

    private void requireEnabled() {
        if (!enabled) throw new NotFoundException();
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
                .orElseThrow(() -> new BadRequestException("No open Secteur: the Super admin opens one first."));
    }
}
