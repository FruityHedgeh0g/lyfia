package fr.fruityhedgeh0g.dtos;

import fr.fruityhedgeh0g.entities.CarouselItemEntity;

import java.util.UUID;

/** A carousel slide; {@code order} is its place, from 1; {@code sectorId} null: the whole site. */
public record CarouselItemDto(UUID id, String title, String caption, UUID mediaId, String linkTo, boolean active, int order,
                              UUID sectorId) {

    public static CarouselItemDto of(CarouselItemEntity item) {
        return new CarouselItemDto(item.getItemId(), item.getTitle(), item.getCaption(),
                item.getMedia() == null ? null : item.getMedia().getMediaId(), item.getLinkTo(), item.isActive(), item.getPosition(),
                item.getSector() == null ? null : item.getSector().getSectorId());
    }

    /** What the Bureau writes about a slide; {@code sectorId} is chosen by the Super admin only. */
    public record Input(String title, String caption, UUID mediaId, String linkTo, Boolean active, UUID sectorId) {}
}
