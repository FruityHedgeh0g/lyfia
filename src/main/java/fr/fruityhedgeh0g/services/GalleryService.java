package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.dtos.galleryDtos.AlbumDto;
import fr.fruityhedgeh0g.dtos.galleryDtos.GalleryPhotoChangeDto;
import fr.fruityhedgeh0g.dtos.galleryDtos.GalleryPhotoDto;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.GalleryPhotoEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.medias.MediaEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.MediaVariantEnum;
import fr.fruityhedgeh0g.enums.VisibiliteEnum;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.GalleryPhotoRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.security.Viewer;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalGroupService;
import fr.fruityhedgeh0g.utilities.logging.Logged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Galerie: Photos de la Galerie, each of one Secteur and one Année, optionally of an Event and of Groupes, whose
 * Albums follow (ADR 0013). The Bureau deposits, describes, sets the Visibilité of and deletes its Secteur's photos;
 * everyone sees what its Visibilité allows them.
 */
@Logged
@ApplicationScoped
public class GalleryService {

    /** The first Année a photo can be of. */
    static final int FIRST_ANNEE = 1990;

    @Inject GalleryPhotoRepository photoRepository;
    @Inject SectorRepository sectorRepository;
    @Inject EventRepository eventRepository;
    @Inject GroupRepository groupRepository;
    @Inject InternalGroupService internalGroupService;
    @Inject MediaFileService mediaFileService;
    @Inject MediaUses mediaUses;
    @Inject GallerySettings settings;
    @Inject FeatureLevers featureLevers;
    @Inject Viewer viewer;

    /** What a deposit tells about the photo, with its original and the two variants its depositor's browser made. */
    public record Deposit(UUID sectorId, UUID eventId, Integer annee, List<UUID> groupIds, String alt,
                          MediaFileService.Upload original, MediaFileService.Upload affichage, MediaFileService.Upload vignette) {}

    /** What a Chef de groupe proposes: a photo of one of the Events of their Groupe's Secteur, with its variants. */
    public record Proposition(UUID eventId, String alt, MediaFileService.Upload original, MediaFileService.Upload affichage,
                              MediaFileService.Upload vignette) {}

    /** The Albums the viewer may open, latest Année first; while Galerie photos is off, none for the public. */
    public List<AlbumDto> albums() {
        featureLevers.requireForPublic(FeatureEnum.GALERIE_PHOTOS);
        Map<SectorEntity, Map<Integer, Long>> counts = photoRepository.listAllPhotos().stream()
                .filter(this::visible)
                .collect(Collectors.groupingBy(GalleryPhotoEntity::getSector,
                        Collectors.groupingBy(GalleryPhotoEntity::getAnnee, Collectors.counting())));
        return counts.entrySet().stream()
                .flatMap(bySector -> bySector.getValue().entrySet().stream()
                        .map(byAnnee -> new AlbumDto(bySector.getKey().getSectorId(), bySector.getKey().getName(), byAnnee.getKey(), byAnnee.getValue())))
                .sorted(Comparator.comparing(AlbumDto::sectorName).thenComparing(AlbumDto::annee, Comparator.reverseOrder()))
                .toList();
    }

    /** An Album, narrowed to an Event or a Groupe if asked: the photos the viewer may see. */
    public List<GalleryPhotoDto> album(UUID sectorId, int annee, UUID eventId, UUID groupId) {
        SectorEntity sector = sectorOrThrow(sectorId);
        featureLevers.requireForPublic(FeatureEnum.GALERIE_PHOTOS, sector);
        return photoRepository.listAlbum(sectorId, annee).stream()
                .filter(this::visible)
                .filter(p -> eventId == null || (p.getEvent() != null && p.getEvent().getEventId().equals(eventId)))
                .filter(p -> groupId == null || p.getGroups().stream().anyMatch(g -> g.getGroupId().equals(groupId)))
                .map(p -> manages(p.getSector()) ? managedDto(p) : GalleryPhotoDto.of(p, false, List.of()))
                .toList();
    }

    /** The Bureau of the Secteur deposits a photo; it starts as a Brouillon. */
    @Transactional
    public GalleryPhotoDto deposit(Deposit deposit) {
        featureLevers.require(FeatureEnum.DEPOT_MEDIAS);
        SectorEntity sector = depositSectorOf(deposit.sectorId());
        requireManaged(sector);
        GallerySettings.Limits limits = settings.limits();
        MediaFileService.requireImage(deposit.original(), limits.maxBytes());
        MediaFileService.requireImage(deposit.affichage(), limits.maxBytes());
        MediaFileService.requireImage(deposit.vignette(), limits.maxBytes());

        GalleryPhotoEntity photo = GalleryPhotoEntity.builder()
                .sector(sector)
                .depositedBy(viewer.personId().orElse(null))
                .depositedAt(LocalDateTime.now())
                .build();
        tell(photo, deposit.eventId(), deposit.annee(), deposit.groupIds());
        photo.setMedia(mediaFileService.store(deposit.original(),
                Map.of(MediaVariantEnum.AFFICHAGE, deposit.affichage(), MediaVariantEnum.VIGNETTE, deposit.vignette()), deposit.alt()));
        photoRepository.persist(photo);
        return managedDto(photo);
    }

    /**
     * A Chef de groupe proposes a photo of an Event for the Groupe they lead: a Brouillon of that Event and that
     * Groupe, waiting for the Bureau. Dépôt de médias stops Propositions too.
     */
    @Transactional
    public GalleryPhotoDto propose(UUID chefId, Proposition proposition) {
        featureLevers.require(FeatureEnum.DEPOT_MEDIAS);
        GroupEntity group = internalGroupService.doGetEntityLedBy(chefId)
                .orElseThrow(() -> new ForbiddenActionException(chefId + " leads no Groupe."));
        SectorEntity sector = group.getSector();
        if (sector == null || sector.isClosed())
            throw new InvalidResourceException("A Secteur fermé is read-only: " + group.getGroupId());
        if (proposition.eventId() == null) throw new InvalidResourceException("A Proposition is of an Event.");
        GallerySettings.Limits limits = settings.limits();
        MediaFileService.requireImage(proposition.original(), limits.maxBytes());
        MediaFileService.requireImage(proposition.affichage(), limits.maxBytes());
        MediaFileService.requireImage(proposition.vignette(), limits.maxBytes());

        GalleryPhotoEntity photo = GalleryPhotoEntity.builder()
                .sector(sector)
                .proposition(true)
                .depositedBy(chefId)
                .depositedAt(LocalDateTime.now())
                .build();
        tell(photo, proposition.eventId(), null, List.of(group.getGroupId()));
        photo.setMedia(mediaFileService.store(proposition.original(),
                Map.of(MediaVariantEnum.AFFICHAGE, proposition.affichage(), MediaVariantEnum.VIGNETTE, proposition.vignette()), proposition.alt()));
        photoRepository.persist(photo);
        return GalleryPhotoDto.of(photo, false, List.of());
    }

    /** A Chef de groupe's Propositions still waiting for the Bureau. */
    public List<GalleryPhotoDto> propositionsBy(UUID chefId) {
        return photoRepository.listPropositionsBy(chefId).stream().map(p -> GalleryPhotoDto.of(p, false, List.of())).toList();
    }

    /** The Chef withdraws a Proposition of theirs until the Bureau validates it. */
    @Transactional
    public void withdraw(UUID chefId, UUID photoId) {
        GalleryPhotoEntity photo = photoRepository.findByIdOptional(photoId)
                .orElseThrow(() -> new UnknownResourceException("Photo not found: " + photoId));
        if (!chefId.equals(photo.getDepositedBy()))
            throw new ForbiddenActionException(chefId + " did not propose " + photoId);
        if (!photo.isProposition())
            throw new InvalidResourceException("A validated photo is the Bureau's: " + photoId);
        MediaEntity media = photo.getMedia();
        photoRepository.delete(photo);
        photoRepository.flush();
        mediaFileService.delete(media);
    }

    /** The Propositions waiting for the Bureau of the Secteur ("À valider"). */
    public List<GalleryPhotoDto> propositionsOf(UUID sectorId) {
        SectorEntity sector = sectorOrThrow(sectorId);
        if (!manages(sector)) throw new ForbiddenActionException("The Secteur " + sectorId + " is not yours.");
        return photoRepository.listPropositionsOf(sectorId).stream().map(this::managedDto).toList();
    }

    /** The Bureau validates a Proposition, choosing its Visibilité; refusing one is deleting it. */
    @Transactional
    public GalleryPhotoDto validate(UUID photoId, VisibiliteEnum visibilite) {
        GalleryPhotoEntity photo = managedPhotoOrThrow(photoId);
        photo.setProposition(false);
        photo.setVisibilite(visibilite);
        return managedDto(photo);
    }

    /** The Bureau corrects what a photo tells: its description, Event or Année, and Groupes. */
    @Transactional
    public GalleryPhotoDto change(UUID photoId, GalleryPhotoChangeDto change) {
        GalleryPhotoEntity photo = managedPhotoOrThrow(photoId);
        tell(photo, change.eventId(), change.annee(), change.groupIds());
        photo.getMedia().setAlt(change.alt() == null || change.alt().isBlank() ? null : change.alt().trim());
        return managedDto(photo);
    }

    /** The Bureau gives a selection of its Secteur's photos one Visibilité. */
    @Transactional
    public List<GalleryPhotoDto> setVisibilite(List<UUID> photoIds, VisibiliteEnum visibilite) {
        List<GalleryPhotoEntity> photos = photoIds.stream().distinct().map(this::managedPhotoOrThrow).toList();
        // A photo used on the public site stays Publique
        if (visibilite != VisibiliteEnum.PUBLIQUE)
            photos.forEach(photo -> mediaUses.requireUnused(photo.getMedia().getMediaId(), "A photo in use stays Publique"));
        photos.forEach(photo -> {
            photo.setVisibilite(visibilite);
            photo.setProposition(false);
        });
        return photos.stream().map(this::managedDto).toList();
    }

    /** The Bureau deletes a photo, on request for instance (image rights), with its files. */
    @Transactional
    public void delete(UUID photoId) {
        GalleryPhotoEntity photo = managedPhotoOrThrow(photoId);
        MediaEntity media = photo.getMedia();
        mediaUses.requireUnused(media.getMediaId(), "A photo in use is not deleted");
        photoRepository.delete(photo);
        photoRepository.flush();
        mediaFileService.delete(media);
    }

    /**
     * Whether the viewer may have a Media's file: anyone for a Media outside the Galerie, what its Visibilité allows
     * for a Photo de la Galerie; {@code isPublic} tells whether anyone may, so the file can be cached for everyone.
     */
    public record Access(boolean allowed, boolean isPublic) {}

    public Access accessTo(MediaEntity media) {
        return photoRepository.findByMedia(media.getMediaId())
                .map(photo -> new Access(seesWhateverTheFeature(photo), photo.getVisibilite() == VisibiliteEnum.PUBLIQUE
                        && !photo.getSector().isClosed()))
                .orElse(new Access(true, true));
    }

    /** For the Bureau of its Secteur: its Visibilité, and where the site uses it. */
    private GalleryPhotoDto managedDto(GalleryPhotoEntity photo) {
        return GalleryPhotoDto.of(photo, true, mediaUses.usedBy(photo.getMedia().getMediaId()));
    }

    public GallerySettings.Limits limits() {
        return settings.limits();
    }

    /** Its Event gives its Année; without one the Année is given. Its Event and Groupes are of its Secteur. */
    private void tell(GalleryPhotoEntity photo, UUID eventId, Integer annee, List<UUID> groupIds) {
        EventEntity event = eventId == null ? null : eventRepository.findByIdOptional(eventId)
                .orElseThrow(() -> new UnknownResourceException("Event not found: " + eventId));
        if (event != null && !photo.isOf(event.getSector()))
            throw new InvalidResourceException("The Event " + eventId + " is not of the photo's Secteur.");
        int year = event != null ? event.getStartDateTime().getYear() : Objects.requireNonNullElse(annee, 0);
        if (year < FIRST_ANNEE || year > LocalDate.now().getYear() + 1)
            throw new InvalidResourceException("A photo is of an Année between " + FIRST_ANNEE + " and next year: " + annee);

        HashSet<GroupEntity> groups = new HashSet<>();
        for (UUID groupId : Optional.ofNullable(groupIds).orElse(List.of()).stream().distinct().toList()) {
            GroupEntity group = groupRepository.findByIdOptional(groupId)
                    .orElseThrow(() -> new UnknownResourceException("Group not found: " + groupId));
            if (!photo.isOf(group.getSector()))
                throw new InvalidResourceException("The Groupe " + groupId + " is not of the photo's Secteur.");
            groups.add(group);
        }
        photo.setEvent(event);
        photo.setAnnee(year);
        photo.setGroups(groups);
    }

    /** What the viewer sees in the Galerie: nothing of a Secteur where Galerie photos is off, but for the Bureau. */
    private boolean visible(GalleryPhotoEntity photo) {
        if (!viewer.preparesContent() && !featureLevers.isOn(FeatureEnum.GALERIE_PHOTOS, photo.getSector())) return false;
        return seesWhateverTheFeature(photo);
    }

    /** A Secteur fermé's photos for the Super admin only; then the Bureau of its Secteur, its depositor, its Visibilité. */
    private boolean seesWhateverTheFeature(GalleryPhotoEntity photo) {
        if (photo.getSector().isClosed()) return viewer.seesClosedSecteurs();
        if (manages(photo.getSector())) return true;
        if (photo.getDepositedBy() != null && viewer.personId().filter(photo.getDepositedBy()::equals).isPresent()) return true;
        return switch (photo.getVisibilite()) {
            case PUBLIQUE -> true;
            case INSCRITS -> viewer.signedIn();
            case BROUILLON -> false;
        };
    }

    /** The Bureau and Admins of the Secteur, the Super admin of every one (ADR 0004). */
    private boolean manages(SectorEntity sector) {
        return viewer.preparesContent() && viewer.scope().covers(sector);
    }

    private void requireManaged(SectorEntity sector) {
        if (!manages(sector))
            throw new ForbiddenActionException("The Secteur " + sector.getSectorId() + " is not yours.");
        if (sector.isClosed())
            throw new InvalidResourceException("A Secteur fermé is read-only: " + sector.getSectorId());
    }

    private GalleryPhotoEntity managedPhotoOrThrow(UUID photoId) {
        GalleryPhotoEntity photo = photoRepository.findByIdOptional(photoId)
                .orElseThrow(() -> new UnknownResourceException("Photo not found: " + photoId));
        requireManaged(photo.getSector());
        return photo;
    }

    /** The Super admin names the Secteur; the Bureau deposits in its own. */
    private SectorEntity depositSectorOf(UUID sectorId) {
        UUID chosen = sectorId != null ? sectorId : viewer.scope().sectorId();
        if (chosen == null) throw new InvalidResourceException("Name the Secteur of the photo.");
        return sectorOrThrow(chosen);
    }

    private SectorEntity sectorOrThrow(UUID sectorId) {
        if (sectorId == null) throw new InvalidResourceException("Name the Secteur.");
        return sectorRepository.findByIdOptional(sectorId)
                .orElseThrow(() -> new UnknownResourceException("Sector not found: " + sectorId));
    }
}
