package fr.fruityhedgeh0g.dtos.galleryDtos;

import java.util.UUID;

/** An Album the viewer may open: a Secteur's photos for one Année, and how many of them the viewer sees (ADR 0013). */
public record AlbumDto(UUID sectorId, String sectorName, int annee, long count) {
}
