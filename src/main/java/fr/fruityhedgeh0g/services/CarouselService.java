package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.security.SecteurScope;
import fr.fruityhedgeh0g.security.Viewer;
import fr.fruityhedgeh0g.dtos.CarouselItemDto;
import fr.fruityhedgeh0g.entities.CarouselItemEntity;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.CarouselItemRepository;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The home page's carousel, per Secteur (ADR 0004): everyone sees the active slides; the Bureau of a Secteur writes,
 * orders and puts aside its own slides, the Super admin any slide, and those of the whole site.
 */
@ApplicationScoped
public class CarouselService {

    @Inject CarouselItemRepository itemRepository;
    @Inject MediaRepository mediaRepository;
    @Inject SectorRepository sectorRepository;
    @Inject Viewer viewer;

    /**
     * The active slides, plus the slides put aside of the Secteurs this person manages when {@code preparer};
     * {@code managed}: only the slides of the Secteurs this person manages (the Carrousel admin screen).
     */
    public List<CarouselItemDto> list(boolean preparer, boolean managed) {
        SecteurScope scope = viewer.scope();
        return itemRepository.listInOrder().stream()
                .filter(item -> !item.isInClosedSector() || viewer.seesClosedSecteurs())
                .filter(item -> managed ? scope.covers(item.getSector()) : item.isActive() || (preparer && scope.covers(item.getSector())))
                .map(CarouselItemDto::of)
                .toList();
    }

    @Transactional
    public CarouselItemDto create(CarouselItemDto.Input input) {
        CarouselItemEntity item = new CarouselItemEntity();
        item.setPosition(itemRepository.listInOrder().stream().mapToInt(CarouselItemEntity::getPosition).max().orElse(0) + 1);
        item.setSector(sectorFor(input.sectorId()));
        apply(item, input);
        itemRepository.persist(item);
        return CarouselItemDto.of(item);
    }

    @Transactional
    public CarouselItemDto update(UUID itemId, CarouselItemDto.Input input) {
        CarouselItemEntity item = managedItemOrThrow(itemId);
        apply(item, input);
        return CarouselItemDto.of(item);
    }

    @Transactional
    public void delete(UUID itemId) {
        itemRepository.delete(managedItemOrThrow(itemId));
    }

    /** Swaps the slide with the one before ({@code up}) or after it among its Secteur's; nothing at either end. */
    @Transactional
    public List<CarouselItemDto> move(UUID itemId, boolean up) {
        CarouselItemEntity moved = managedItemOrThrow(itemId);
        List<CarouselItemEntity> items = itemRepository.listInOrder().stream()
                .filter(item -> sameSector(item, moved))
                .toList();
        int index = items.indexOf(moved);
        int other = up ? index - 1 : index + 1;
        if (other >= 0 && other < items.size()) {
            int position = items.get(index).getPosition();
            items.get(index).setPosition(items.get(other).getPosition());
            items.get(other).setPosition(position);
        }
        return list(true, true);
    }

    private static boolean sameSector(CarouselItemEntity a, CarouselItemEntity b) {
        UUID first = a.getSector() == null ? null : a.getSector().getSectorId();
        UUID second = b.getSector() == null ? null : b.getSector().getSectorId();
        return Objects.equals(first, second);
    }

    /** A slide is for the writer's own Secteur; the Super admin chooses one, or none for the whole site. */
    private SectorEntity sectorFor(UUID chosen) {
        SecteurScope scope = viewer.scope();
        if (!scope.everySecteur()) {
            if (scope.sectorId() == null) throw new ForbiddenActionException("A slide belongs to its writer's Secteur.");
            return sectorRepository.findById(scope.sectorId());
        }
        if (chosen == null) return null;
        SectorEntity sector = sectorRepository.findByIdOptional(chosen)
                .orElseThrow(() -> new UnknownResourceException("Sector not found: " + chosen));
        if (sector.isClosed()) throw new InvalidResourceException("A Secteur fermé gets no new slide.");
        return sector;
    }

    private CarouselItemEntity managedItemOrThrow(UUID itemId) {
        CarouselItemEntity item = itemRepository.findByIdOptional(itemId)
                .filter(i -> !i.isInClosedSector() || viewer.seesClosedSecteurs())
                .orElseThrow(() -> new UnknownResourceException("Carousel slide not found: " + itemId));
        if (!viewer.scope().covers(item.getSector()))
            throw new ForbiddenActionException("A slide is managed by its own Secteur's Bureau.");
        if (item.isInClosedSector()) throw new InvalidResourceException("A Secteur fermé is read-only.");
        return item;
    }

    private void apply(CarouselItemEntity item, CarouselItemDto.Input input) {
        if (input.title() == null || input.title().isBlank())
            throw new InvalidResourceException("A slide has a title.");
        String linkTo = input.linkTo() == null || input.linkTo().isBlank() ? null : input.linkTo().trim();
        if (linkTo != null && (!linkTo.startsWith("/") || linkTo.startsWith("//")))
            throw new InvalidResourceException("A slide links to a page of the site: " + linkTo);
        item.setTitle(input.title().trim());
        item.setCaption(input.caption());
        item.setLinkTo(linkTo);
        item.setActive(input.active() == null || input.active());
        item.setMedia(input.mediaId() == null ? null : mediaRepository.findByIdOptional(input.mediaId())
                .orElseThrow(() -> new InvalidResourceException("Unknown media: " + input.mediaId())));
    }
}
