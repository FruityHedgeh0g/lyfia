package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.eventDtos.EventDto;
import fr.fruityhedgeh0g.dtos.eventDtos.DemandeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.GroupRosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.MonGroupeDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RegistrationDto;
import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterEntryDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterExportDto;
import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.JourneeRegistrationEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.enums.PosteEnum;
import fr.fruityhedgeh0g.enums.RegistrationStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.exceptions.ConducteurNotFoundException;
import fr.fruityhedgeh0g.exceptions.SignUpRequirementException;
import fr.fruityhedgeh0g.dtos.eventDtos.DechargeDto;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.PhoneRequiredException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.JourneeRegistrationRepository;
import fr.fruityhedgeh0g.security.Viewer;
import fr.fruityhedgeh0g.services.interfaces.EventService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalGroupService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalSectorService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalUserService;
import fr.fruityhedgeh0g.utilities.NameMatching;
import fr.fruityhedgeh0g.utilities.export.RosterSpreadsheet;
import fr.fruityhedgeh0g.utilities.mappers.EventMapper;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class EventServiceImpl implements EventService {

    @Inject
    EventRepository eventRepository;

    @Inject
    InternalSectorService internalSectorService;

    @Inject
    EventMapper eventMapper;

    @Inject
    EventRegistrationRepository registrationRepository;

    @Inject
    JourneeRegistrationRepository journeeRepository;

    @Inject
    InternalUserService internalUserService;

    @Inject
    InternalGroupService internalGroupService;

    @Inject
    Viewer viewer;

    @Inject
    FeatureLevers featureLevers;

    @Inject
    DechargeService dechargeService;

    /** A Journée's sign-ups in the order they were taken: the order of its Liste d'attente. */
    private static final Comparator<JourneeRegistrationEntity> BY_SIGN_UP = Comparator
            .comparing(JourneeRegistrationEntity::getSignedUpAt)
            .thenComparing(j -> j.getRegistration().getSignedUpAt())
            .thenComparing(j -> j.getRegistration().getRegistrationId());

    @Override
    public List<EventDto> listAll(boolean seesPlanification, UUID viewerId) {
        List<EventEntity> events = seesPlanification ? eventRepository.listAll() : new ArrayList<>(eventRepository.listOutsidePlanification());
        // A Chef sees the Events in Planification they already ride at with their Groupe (ADR 0010)
        if (!seesPlanification && viewerId != null)
            registrationRepository.listByPerson(viewerId).stream()
                    .map(EventRegistrationEntity::getEvent)
                    .filter(event -> event.getStatus() == EventStatusEnum.PLANIFICATION)
                    .forEach(events::add);
        return events.stream().filter(this::visible).map(eventMapper::toDto).toList();
    }

    @Override
    public EventDto getById(UUID eventId, boolean seesPlanification, UUID viewerId) {
        return eventMapper.toDto(
                eventRepository.findByIdOptional(eventId)
                        .filter(this::visible)
                        .filter(event -> seesPlanification || event.getStatus() != EventStatusEnum.PLANIFICATION
                                || (viewerId != null && registrationRepository.findByEventAndPerson(eventId, viewerId).isPresent()))
                        .orElseThrow(() -> new UnknownResourceException("Event not found: " + eventId))
        );
    }

    @Override
    @Transactional
    public EventDto create(EventDto eventDto) {
        if (eventDto.getSectorId() == null)
            throw new InvalidResourceException("An Event belongs to a Secteur.");
        SectorEntity sector = internalSectorService.doGetEntityById(eventDto.getSectorId())
                .orElseThrow(() -> new UnknownResourceException("Sector not found: " + eventDto.getSectorId()));
        if (!viewer.scope().covers(sector))
            throw new ForbiddenActionException("An Event is created in your own Secteur only.");
        if (sector.isClosed())
            throw new InvalidResourceException("A Secteur fermé gets no new Event.");

        EventEntity event = eventMapper.toEntity(eventDto);
        event.setStatus(EventStatusEnum.PLANIFICATION);
        event.setSector(sector);
        normalizeMaximum(event);
        validate(event);
        eventRepository.persist(event);
        // From its Planification on, each Groupe's Chef rides with it (ADR 0010)
        internalGroupService.doListEntitiesOfSector(sector.getSectorId()).forEach(group -> enrollChef(event, group));
        return eventMapper.toDto(event);
    }

    /**
     * The Events follow each Affectation until under way (ADR 0010): the Groupe's new Chef rides with it, and the
     * previous one stays signed up, without a Groupe.
     */
    void onAffectationChange(@Observes AffectationChange change) {
        GroupEntity group = internalGroupService.doGetEntityById(change.groupId()).orElse(null);
        if (group == null || group.getSector() == null) return;
        UUID chefId = group.getChef() == null ? null : group.getChef().getUserId();
        eventRepository.list("sector.sectorId", group.getSector().getSectorId()).stream()
                .filter(EventServiceImpl::followsAffectations)
                .forEach(event -> {
                    if (change.previousChefId() != null && !change.previousChefId().equals(chefId))
                        registrationRepository.findByEventAndPerson(event.getEventId(), change.previousChefId())
                                .filter(previous -> previous.ridesWith(group))
                                .ifPresent(EventRegistrationEntity::leaveGroup);
                    enrollChef(event, group);
                });
    }

    /**
     * A Groupe changes kind only while nobody but its Chef is with it, or asks for it, at an Event still to come;
     * the Chef then holds the Poste of the new kind there (ADR 0010).
     */
    void onGroupKindChange(@Observes GroupKindChange change) {
        GroupEntity group = internalGroupService.doGetEntityById(change.groupId()).orElse(null);
        if (group == null) return;
        List<EventRegistrationEntity> concerned = registrationRepository.listByGroup(group.getGroupId()).stream()
                .filter(r -> !r.getEvent().currentStatus().isFinal())
                .toList();
        if (concerned.stream().anyMatch(r -> !r.isChefOfItsGroup()))
            throw new InvalidResourceException("A Groupe changes kind only while nobody but its Chef is with it at an Event to come.");
        concerned.forEach(chef -> chef.leadGroup(group, chef.getEvent().journees()));
    }

    /** Until an Event is under way (En cours, Archivé) or Annulé, its Groupes' Chefs ride with them. */
    private static boolean followsAffectations(EventEntity event) {
        EventStatusEnum current = event.currentStatus();
        return !event.isInClosedSector() && (current == EventStatusEnum.PLANIFICATION || current.acceptsSignUps());
    }

    /**
     * The Groupe's Chef is at every Journée of the Event with it, at the Poste of its kind, with a confirmed place
     * beyond any maximum and without the phone number asked of a sign-up: they were signed up, not signing up (ADR 0010).
     */
    private void enrollChef(EventEntity event, GroupEntity group) {
        UserEntity chef = group.getChef();
        if (chef == null) return;
        EventRegistrationEntity registration = registrationRepository.findByEventAndPerson(event.getEventId(), chef.getUserId())
                .orElseGet(() -> {
                    EventRegistrationEntity created = EventRegistrationEntity.of(event, chef, group.getKind().chefPoste());
                    registrationRepository.persist(created);
                    return created;
                });
        registration.leadGroup(group, event.journees());
    }

    /** The Chef leading the Groupe rides with it: only a change of Affectation takes them out (ADR 0010). */
    private static void refuseOnChef(EventRegistrationEntity registration) {
        if (registration.isChefOfItsGroup())
            throw new InvalidResourceException("The Chef leading the Groupe rides with it at this Event: change the Affectation instead.");
    }

    @Override
    @Transactional
    public EventDto update(EventDto eventDto) {
        EventEntity event = managedEventOrThrow(eventDto.getEventId());
        refuseOnFinal(event);
        eventMapper.partialDtoToEntity(event, eventDto);
        normalizeMaximum(event);
        validate(event);
        // The sign-ups of Journées the Event no longer has go; its Chefs are at the new ones
        Set<LocalDate> kept = new HashSet<>(event.journees());
        registrationRepository.listByEvent(event.getEventId()).forEach(registration -> {
            registration.getJournees().stream().filter(j -> !kept.contains(j.getJournee())).toList()
                    .forEach(registration::removeJournee);
            dropIfWithoutJournee(registration);
        });
        if (followsAffectations(event) && event.getSector() != null)
            internalGroupService.doListEntitiesOfSector(event.getSector().getSectorId()).forEach(group -> enrollChef(event, group));
        return eventMapper.toDto(event);
    }

    @Override
    @Transactional
    public EventDto changeStatus(UUID eventId, EventStatusEnum status) {
        EventEntity event = managedEventOrThrow(eventId);
        refuseInClosedSector(event);
        EventStatusEnum current = event.currentStatus();
        if (!current.canMoveTo(status))
            throw new InvalidResourceException("An Event cannot move from " + current.id() + " to " + status.id());
        event.setStatus(status);
        return eventMapper.toDto(event);
    }

    @Override
    @Transactional
    public RegistrationDto signUp(UUID eventId, UUID personId, SignUp signUp) {
        EventEntity event = lockedEventOrThrow(eventId);
        UserEntity person = internalUserService.doGetEntityById(personId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + personId));
        boolean comesWithSomeone = signUp.conducteurName() != null && !signUp.conducteurName().isBlank();
        if (comesWithSomeone && signUp.poste() != PosteEnum.PASSAGER)
            throw new InvalidResourceException("Only a passager names a conducteur.");
        if (comesWithSomeone && signUp.groupId() != null)
            throw new InvalidResourceException("A passager coming with a conducteur takes their Groupe.");
        List<LocalDate> chosen = journeesOf(event, signUp.journees());

        Optional<EventRegistrationEntity> existing = registrationRepository.findByEventAndPerson(eventId, personId);
        if (existing.isPresent() && existing.get().getPoste() != signUp.poste())
            throw new InvalidResourceException(personId + " is already signed up for " + eventId + " as " + existing.get().getPoste().id());
        List<LocalDate> added = existing.map(r -> chosen.stream().filter(d -> r.journee(d).isEmpty()).toList()).orElse(chosen);
        if (existing.isPresent() && added.isEmpty()) return toDto(existing.get());
        featureLevers.require(FeatureEnum.INSCRIPTION_EVENEMENTS, event.getSector());
        requireSignUpOpenTo(event, person);
        UUID decharge = requireDechargeOf(person, existing.orElse(null), added, signUp.dechargeSignataire());

        EventRegistrationEntity conducteur = comesWithSomeone ? conducteurNamed(eventId, signUp.conducteurName(), personId, added) : null;
        GroupEntity asked = signUp.groupId() == null || existing.isPresent() ? null : groupOrThrow(signUp.groupId());

        EventRegistrationEntity registration = existing.orElseGet(() -> {
            EventRegistrationEntity created = EventRegistrationEntity.of(event, person, signUp.poste());
            registrationRepository.persist(created);
            return created;
        });
        if (asked != null) registration.requestGroup(asked);
        if (decharge != null) registration.approveDecharge(signUp.dechargeSignataire(), decharge);
        addJournees(event, registration, added, conducteur);
        if (existing.isEmpty() && conducteur != null) comeWith(event, registration, conducteur);
        return toDto(registration);
    }

    /**
     * Each Journée a confirmed place while Ouvert and under its maximum, otherwise on its Liste d'attente; behind a
     * conducteur free that Journée, the passager waits as they do, and is refused rather than separated from a
     * confirmed conducteur when no place is left (including while Complet).
     */
    private void addJournees(EventEntity event, EventRegistrationEntity registration, List<LocalDate> journees,
                             EventRegistrationEntity conducteur) {
        boolean ouvert = event.currentStatus() == EventStatusEnum.OUVERT;
        for (LocalDate journee : journees) {
            JourneeRegistrationEntity behind = conducteur == null ? null
                    : conducteur.journee(journee).filter(j -> j.getPassager() == null).orElse(null);
            boolean placeLeft = ouvert && placeLeft(event, journee, 1);
            if (behind != null && !behind.isWaiting() && !placeLeft)
                throw new InvalidResourceException("No place is left next to " + conducteur.getPerson().getUserId() + " on " + journee);
            JourneeRegistrationEntity added = registration.addJournee(journee, behind == null ? !placeLeft : behind.isWaiting());
            if (behind != null) added.pairWith(behind);
            // Counted by the checks of the next Journées and sign-ups
            journeeRepository.persist(added);
        }
    }

    /**
     * The birth date is required to sign up; a minor on the first Journée signed up for needs the décharge in force,
     * approved by their legal representative, unless already approved for this Event. Gives the version to record.
     */
    private UUID requireDechargeOf(UserEntity person, EventRegistrationEntity existing, List<LocalDate> added, String signataire) {
        if (!person.hasBirthDate())
            throw new SignUpRequirementException(SignUpRequirementException.BIRTH_DATE_REQUIRED,
                    "A birth date is required to sign up: " + person.getUserId());
        if ((existing != null && existing.hasDecharge()) || added.isEmpty() || !person.isMinorOn(added.getFirst())) return null;
        UUID document = dechargeService.current()
                .orElseThrow(() -> new SignUpRequirementException(SignUpRequirementException.DECHARGE_INDISPONIBLE,
                        "No décharge is uploaded yet: minors cannot sign up."))
                .documentId();
        if (signataire == null || signataire.isBlank())
            throw new SignUpRequirementException(SignUpRequirementException.DECHARGE_REQUISE,
                    "A minor's legal representative approves the décharge: " + person.getUserId());
        return document;
    }

    /** "Je viens avec": the passager takes the conducteur's Groupe (within its maximum) or pending Demande. */
    private void comeWith(EventEntity event, EventRegistrationEntity passager, EventRegistrationEntity conducteur) {
        if (conducteur.getGroup() != null) {
            requireRoomIn(event, conducteur.getGroup(), confirmedJournees(passager), 1);
            passager.setGroup(conducteur.getGroup());
        } else if (conducteur.hasPendingDemande()) passager.requestGroup(conducteur.getDemandeGroup());
    }

    /** The Journées asked for, all of them when none: each must be one of the Event's. */
    private static List<LocalDate> journeesOf(EventEntity event, List<LocalDate> asked) {
        if (asked == null || asked.isEmpty()) return event.journees();
        List<LocalDate> journees = asked.stream().distinct().sorted().toList();
        journees.stream().filter(d -> !event.hasJournee(d)).findFirst().ifPresent(d -> {
            throw new InvalidResourceException("The Event has no Journée on " + d);
        });
        return journees;
    }

    /** The Journées of the sign-up asked for, all of them when none: each must be signed up for. */
    private static List<JourneeRegistrationEntity> journeesOf(EventRegistrationEntity registration, List<LocalDate> asked) {
        if (asked == null || asked.isEmpty()) return List.copyOf(registration.getJournees());
        return asked.stream().distinct().map(registration::journeeOrThrow).toList();
    }

    private static List<LocalDate> confirmedJournees(EventRegistrationEntity registration) {
        return registration.getJournees().stream().filter(j -> !j.isWaiting()).map(JourneeRegistrationEntity::getJournee).toList();
    }

    /**
     * "Je viens avec": the conducteur whose name matches the one typed, among those with no passager yet one of the
     * Journées; the list of who is signed up is never served.
     */
    private EventRegistrationEntity conducteurNamed(UUID eventId, String name, UUID passagerId, List<LocalDate> journees) {
        List<EventRegistrationEntity> free = registrationRepository.listByEvent(eventId).stream()
                .filter(r -> r.getPoste() == PosteEnum.CONDUCTEUR && !r.getPerson().getUserId().equals(passagerId))
                .filter(r -> journees.stream().anyMatch(d -> r.journee(d).filter(j -> j.getPassager() == null).isPresent()))
                .toList();
        return switch (NameMatching.find(name, free, r -> r.getPerson().getFirstName(), r -> r.getPerson().getLastName())) {
            case NameMatching.Outcome.Found<EventRegistrationEntity> found -> found.match();
            case NameMatching.Outcome.Ambiguous<EventRegistrationEntity> ambiguous ->
                    throw new ConducteurNotFoundException("Several conducteurs match '" + name + "' at " + eventId, true);
            case NameMatching.Outcome.NotFound<EventRegistrationEntity> none ->
                    throw new ConducteurNotFoundException("No conducteur free those Journées matches '" + name + "' at " + eventId, false);
        };
    }

    /** A conducteur signed up for the Event, other than the passager themselves. */
    private EventRegistrationEntity conducteurOrThrow(UUID eventId, UUID conducteurId, UUID passagerId) {
        return registrationRepository.findByEventAndPerson(eventId, conducteurId)
                .filter(r -> r.getPoste() == PosteEnum.CONDUCTEUR && !r.getPerson().getUserId().equals(passagerId))
                .orElseThrow(() -> new InvalidResourceException("No conducteur " + conducteurId + " signed up for " + eventId));
    }

    @Override
    @Transactional
    public RegistrationDto requestGroup(UUID eventId, UUID personId, UUID groupId) {
        EventEntity event = eventOrThrow(eventId);
        featureLevers.require(FeatureEnum.INSCRIPTION_EVENEMENTS, event.getSector());
        refuseOnArchived(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        registration.requestGroup(groupOrThrow(groupId));
        return toDto(registration);
    }

    @Override
    @Transactional
    public RegistrationDto decideDemande(UUID eventId, UUID personId, Actor actor, boolean accept) {
        EventEntity event = lockedEventOrThrow(eventId);
        refuseOnArchived(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        if (!registration.hasPendingDemande())
            throw new InvalidResourceException("No pending Demande de groupe for " + personId);
        requireLeaderOrBureau(actor, registration.getDemandeGroup(), event);
        if (accept) {
            // Beyond the maximum any of their Journées the Demande stays pending, on the Groupe's Liste d'attente
            requireRoomIn(event, registration.getDemandeGroup(), registration.journeeDates(), 1);
            registration.acceptDemande();
        } else registration.refuseDemande();
        return toDto(registration);
    }

    @Override
    @Transactional
    public RosterDto placeInGroup(UUID eventId, UUID personId, UUID groupId, boolean withPassager) {
        EventEntity event = lockedEventOrThrow(eventId);
        requireManaged(event);
        refuseOnArchived(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        refuseOnChef(registration);
        if (registration.status() == RegistrationStatusEnum.EN_ATTENTE)
            throw new InvalidResourceException("Only a Participant is placed in a Groupe.");
        GroupEntity group = groupOrThrow(groupId);
        List<EventRegistrationEntity> moving = new ArrayList<>(List.of(registration));
        if (withPassager) passagersOf(registration).stream().filter(p -> !p.isChefOfItsGroup()).forEach(moving::add);
        else endPairingsOf(registration);
        // Each one entering the Groupe takes a place there every Journée they are signed up for
        List<LocalDate> entering = moving.stream().filter(m -> !m.ridesWith(group))
                .flatMap(m -> m.journeeDates().stream()).toList();
        entering.stream().distinct().forEach(d ->
                requireRoomIn(event, group, List.of(d), (int) entering.stream().filter(d::equals).count()));
        moving.forEach(m -> m.placeIn(group));
        if (registration.isPassager()) endPairingsOutside(registration, group);
        return rosterOf(event, null);
    }

    @Override
    @Transactional
    public RosterDto changePoste(UUID eventId, UUID personId, PosteEnum poste) {
        EventEntity event = managedEventOrThrow(eventId);
        refuseOnArchived(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        refuseOnChef(registration);
        registration.changePoste(poste);
        return rosterOf(event, null);
    }

    @Override
    @Transactional
    public RosterDto setGroupMaximum(UUID eventId, UUID groupId, Integer maximum) {
        EventEntity event = lockedEventOrThrow(eventId);
        requireManaged(event);
        refuseOnFinal(event);
        GroupEntity group = groupOrThrow(groupId);
        if (!event.isOfSectorOf(group))
            throw new InvalidResourceException("The Groupe " + groupId + " is not of the Event's Secteur.");
        // Lowering it below the current riders takes nobody out; it only stops new acceptances
        event.setMaximumOf(group, maximum);
        return rosterOf(event, null);
    }

    @Override
    @Transactional
    public RegistrationDto takeOutOfGroup(UUID eventId, UUID personId, Actor actor, boolean withPassager) {
        EventEntity event = eventOrThrow(eventId);
        refuseOnArchived(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        GroupEntity group = registration.getGroup();
        requireLeaderOrBureau(actor, group, event);
        refuseOnChef(registration);
        registration.leaveGroup();
        if (withPassager)
            passagersOf(registration).stream().filter(p -> p.ridesWith(group) && !p.isChefOfItsGroup()).forEach(EventRegistrationEntity::leaveGroup);
        else endPairingsOf(registration);
        if (registration.isPassager()) endPairingsOutside(registration, null);
        return toDto(registration);
    }

    @Override
    @Transactional
    public RegistrationDto pair(UUID eventId, UUID passagerId, LocalDate journee, UUID conducteurId, Actor actor) {
        EventEntity event = lockedEventOrThrow(eventId);
        refuseOnArchived(event);
        EventRegistrationEntity passager = passagerOrThrow(eventId, passagerId, actor, event);
        EventRegistrationEntity conducteur = conducteurOrThrow(eventId, conducteurId, passagerId);
        if (!Objects.equals(groupIdOf(passager), groupIdOf(conducteur)))
            throw new InvalidResourceException("A passager rides behind a conducteur of their own Groupe.");
        JourneeRegistrationEntity passagerJournee = passager.journeeOrThrow(journee);
        JourneeRegistrationEntity conducteurJournee = conducteur.journeeOrThrow(journee);
        if (passagerJournee.isWaiting() != conducteurJournee.isWaiting())
            throw new InvalidResourceException("A passager and their conducteur wait, or ride, together.");
        passagerJournee.pairWith(conducteurJournee);
        return toDto(passager);
    }

    @Override
    @Transactional
    public RegistrationDto unpair(UUID eventId, UUID passagerId, LocalDate journee, Actor actor) {
        EventEntity event = eventOrThrow(eventId);
        refuseOnArchived(event);
        EventRegistrationEntity passager = passagerOrThrow(eventId, passagerId, actor, event);
        passager.journeeOrThrow(journee).unpair();
        return toDto(passager);
    }

    /** A passager's sign-up, for the Bureau or the Chef of the passager's Groupe. */
    private EventRegistrationEntity passagerOrThrow(UUID eventId, UUID passagerId, Actor actor, EventEntity event) {
        EventRegistrationEntity passager = registrationOrThrow(eventId, passagerId);
        if (!passager.isPassager())
            throw new InvalidResourceException(passagerId + " is not a passager at " + eventId);
        requireLeaderOrBureau(actor, passager.getGroup(), event);
        return passager;
    }

    private static UUID groupIdOf(EventRegistrationEntity registration) {
        return registration.getGroup() == null ? null : registration.getGroup().getGroupId();
    }

    /** The passagers riding behind this conducteur, any Journée. */
    private static List<EventRegistrationEntity> passagersOf(EventRegistrationEntity conducteur) {
        return conducteur.getJournees().stream().map(JourneeRegistrationEntity::getPassager).filter(Objects::nonNull)
                .map(JourneeRegistrationEntity::getRegistration).distinct().toList();
    }

    /** A conducteur's passagers stay where they are: their pairings with them end. */
    private static void endPairingsOf(EventRegistrationEntity conducteur) {
        conducteur.getJournees().stream().filter(j -> j.getPassager() != null).forEach(JourneeRegistrationEntity::unpair);
    }

    /** A passager rides only behind a conducteur of their own Groupe: other pairings end. */
    private static void endPairingsOutside(EventRegistrationEntity passager, GroupEntity group) {
        UUID groupId = group == null ? null : group.getGroupId();
        passager.getJournees().stream()
                .filter(j -> j.getConducteur() != null && !Objects.equals(groupIdOf(j.getConducteur().getRegistration()), groupId))
                .forEach(JourneeRegistrationEntity::unpair);
    }

    @Override
    public MonGroupeDto monGroupe(UUID chefId) {
        Optional<GroupEntity> led = internalGroupService.doGetEntityLedBy(chefId);
        if (led.isEmpty()) return new MonGroupeDto(null, List.of());

        GroupEntity group = led.get();
        Map<EventEntity, List<EventRegistrationEntity>> byEvent = registrationRepository.listByGroup(group.getGroupId()).stream()
                .collect(Collectors.groupingBy(EventRegistrationEntity::getEvent, LinkedHashMap::new, Collectors.toList()));
        // Every Event of the Groupe's Secteur a Chef prepares, even before anyone asks for the Groupe
        if (group.getSector() != null)
            eventRepository.list("sector.sectorId", group.getSector().getSectorId()).stream()
                    // From its Planification on: the Chef already rides at it with the Groupe (ADR 0010)
                    .filter(e -> followsAffectations(e) || e.currentStatus() == EventStatusEnum.EN_COURS)
                    .forEach(e -> byEvent.putIfAbsent(e, List.of()));

        List<MonGroupeDto.EventRoster> events = byEvent.entrySet().stream()
                .filter(e -> e.getKey().currentStatus() != EventStatusEnum.ARCHIVE && !e.getKey().isInClosedSector())
                .sorted(Comparator.comparing((Map.Entry<EventEntity, List<EventRegistrationEntity>> e) -> e.getKey().getStartDateTime()))
                .map(e -> {
                    EventEntity event = e.getKey();
                    List<EventRegistrationEntity> all = e.getValue();
                    return new MonGroupeDto.EventRoster(
                            event.getEventId(), event.getName(), event.getStartDateTime(), event.currentStatus(),
                            event.maximumOf(group),
                            all.stream().filter(r -> r.ridesWith(group)).map(r -> toRosterEntry(r, null, false)).toList(),
                            all.stream().filter(r -> r.asksFor(group)).map(r -> toRosterEntry(r, null, false)).toList(),
                            event.journees().stream().map(d -> new MonGroupeDto.JourneeRoster(d,
                                    entriesAt(all, d, r -> r.ridesWith(group), false),
                                    entriesAt(all, d, r -> r.asksFor(group), false))).toList());
                })
                .toList();
        return new MonGroupeDto(GroupRefDto.of(group), events);
    }

    /** The sign-ups for that Journée matching, in that Journée's sign-up order; {@code forBureau} with their personal details. */
    private static List<RosterEntryDto> entriesAt(List<EventRegistrationEntity> all, LocalDate journee,
                                                  Predicate<EventRegistrationEntity> matching, boolean forBureau) {
        return all.stream().filter(matching)
                .flatMap(r -> r.journee(journee).stream())
                .sorted(BY_SIGN_UP)
                .map(j -> toRosterEntry(j.getRegistration(), journee, forBureau))
                .toList();
    }

    /** The Chef who leads the Groupe (by Affectation), or the Bureau of the Event's Secteur (ADR 0004). */
    private void requireLeaderOrBureau(Actor actor, GroupEntity group, EventEntity event) {
        boolean leads = group != null && group.getChef() != null && group.getChef().getUserId().equals(actor.personId());
        boolean managesSecteur = actor.bureau() && viewer.scope().covers(event.getSector());
        if (!leads && !managesSecteur)
            throw new ForbiddenActionException(actor.personId() + " does not lead this Groupe.");
    }

    /** The Bureau and Admin manage only their own Secteur's Events; the Super admin all (ADR 0004). */
    private void requireManaged(EventEntity event) {
        if (!viewer.scope().covers(event.getSector()))
            throw new ForbiddenActionException("The Event " + event.getEventId() + " is not of your Secteur.");
    }

    private EventEntity managedEventOrThrow(UUID eventId) {
        EventEntity event = eventOrThrow(eventId);
        requireManaged(event);
        return event;
    }

    /** Refuses {@code needed} more people in the Groupe beyond its maximum any of those Journées; no maximum means no limit. */
    private void requireRoomIn(EventEntity event, GroupEntity group, List<LocalDate> journees, int needed) {
        Integer maximum = event.maximumOf(group);
        if (maximum == null) return;
        for (LocalDate journee : journees)
            if (journeeRepository.countInGroup(event.getEventId(), group.getGroupId(), journee) + needed > maximum)
                throw new InvalidResourceException("The Groupe is at its maximum of " + maximum + " on " + journee + ".");
    }

    private GroupEntity groupOrThrow(UUID groupId) {
        return internalGroupService.doGetEntityById(groupId)
                .orElseThrow(() -> new UnknownResourceException("Group not found: " + groupId));
    }

    @Override
    @Transactional
    public void withdraw(UUID eventId, UUID personId, List<LocalDate> journees, boolean withPassager) {
        EventEntity event = eventOrThrow(eventId);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        refuseOnArchived(event);
        refuseOnChef(registration);
        leave(registration, journeesOf(registration, journees), withPassager);
    }

    /** Leaves those Journées, with the passager behind on each or leaving them there; a sign-up without Journée goes. */
    private void leave(EventRegistrationEntity registration, List<JourneeRegistrationEntity> journees, boolean withPassager) {
        journees.forEach(journee -> {
            JourneeRegistrationEntity passager = journee.getPassager();
            registration.removeJournee(journee);
            if (withPassager && passager != null && !passager.getRegistration().isChefOfItsGroup()) {
                EventRegistrationEntity theirs = passager.getRegistration();
                theirs.removeJournee(passager);
                dropIfWithoutJournee(theirs);
            }
        });
        dropIfWithoutJournee(registration);
        registrationRepository.flush();
    }

    private void dropIfWithoutJournee(EventRegistrationEntity registration) {
        if (registration.getJournees().isEmpty()) registrationRepository.delete(registration);
    }

    @Override
    public List<RegistrationDto> registrationsOf(UUID personId) {
        return registrationRepository.listByPerson(personId).stream()
                .filter(r -> visible(r.getEvent()))
                .map(EventServiceImpl::toDto)
                .toList();
    }

    @Override
    public RosterDto roster(UUID eventId, LocalDate journee) {
        EventEntity event = managedEventOrThrow(eventId);
        if (journee != null && !event.hasJournee(journee))
            throw new InvalidResourceException("The Event has no Journée on " + journee);
        return rosterOf(event, journee);
    }

    @Override
    public RosterExportDto exportRoster(UUID eventId) {
        EventEntity event = managedEventOrThrow(eventId);
        featureLevers.require(FeatureEnum.EXPORT_LISTE, event.getSector());
        return new RosterExportDto(RosterSpreadsheet.fileName(event.getName(), event.getStartDateTime().toLocalDate()),
                RosterSpreadsheet.of(event.journees().stream().map(d -> rosterOf(event, d)).toList()));
    }

    @Override
    @Transactional
    public RosterDto promote(UUID eventId, UUID personId, List<LocalDate> journees) {
        EventEntity event = lockedEventOrThrow(eventId);
        requireManaged(event);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        refuseOnArchived(event);
        List<JourneeRegistrationEntity> waiting = journeesOf(registration, journees).stream()
                .filter(JourneeRegistrationEntity::isWaiting).toList();
        if (waiting.isEmpty())
            throw new InvalidResourceException(personId + " is already a Participant.");
        for (JourneeRegistrationEntity journee : waiting) {
            // A pair moves up together, taking two places
            JourneeRegistrationEntity partner = journee.partner();
            int needed = partner != null && partner.isWaiting() ? 2 : 1;
            if (!placeLeft(event, journee.getJournee(), needed))
                throw new InvalidResourceException("The Event is at its maximum of " + event.getMaxParticipants()
                        + " Participants on " + journee.getJournee() + ".");
            journee.setWaiting(false);
            if (partner != null) partner.setWaiting(false);
        }
        return rosterOf(event, null);
    }

    @Override
    @Transactional
    public RosterDto removeFromRoster(UUID eventId, UUID personId, List<LocalDate> journees, boolean withPassager) {
        EventEntity event = managedEventOrThrow(eventId);
        EventRegistrationEntity registration = registrationOrThrow(eventId, personId);
        refuseOnArchived(event);
        refuseOnChef(registration);
        leave(registration, journeesOf(registration, journees), withPassager);
        return rosterOf(event, null);
    }

    /** The roster for that Journée, the Event's first when none. */
    private RosterDto rosterOf(EventEntity event, LocalDate asked) {
        List<LocalDate> journees = event.journees();
        LocalDate journee = asked != null ? asked : journees.isEmpty() ? null : journees.getFirst();
        List<EventRegistrationEntity> all = registrationRepository.listByEvent(event.getEventId());
        List<GroupEntity> groups = event.getSector() == null ? List.of()
                : internalGroupService.doListEntitiesOfSector(event.getSector().getSectorId());
        List<JourneeRegistrationEntity> that = all.stream().flatMap(r -> r.journee(journee).stream()).sorted(BY_SIGN_UP).toList();
        return new RosterDto(
                event.getEventId(),
                journee,
                journees.stream().map(d -> {
                    List<JourneeRegistrationEntity> at = all.stream().flatMap(r -> r.journee(d).stream()).toList();
                    int waiting = (int) at.stream().filter(JourneeRegistrationEntity::isWaiting).count();
                    return new RosterDto.JourneeCountDto(d, at.size() - waiting, waiting);
                }).toList(),
                event.getMaxParticipants(),
                that.stream().filter(j -> !j.isWaiting()).map(j -> toRosterEntry(j.getRegistration(), journee, true)).toList(),
                that.stream().filter(JourneeRegistrationEntity::isWaiting).map(j -> toRosterEntry(j.getRegistration(), journee, true)).toList(),
                groups.stream().map(group -> new GroupRosterDto(
                        GroupRefDto.of(group),
                        event.maximumOf(group),
                        entriesAt(all, journee, r -> r.ridesWith(group), true),
                        entriesAt(all, journee, r -> r.asksFor(group), true)
                )).toList()
        );
    }

    /**
     * One person on the roster, with that Journée's place and pairing (their first Journée's when none); the Bureau
     * also sees their e-mail, birth date and décharge (insurance of minors), a Chef de groupe does not.
     */
    private static RosterEntryDto toRosterEntry(EventRegistrationEntity registration, LocalDate journee, boolean forBureau) {
        UserEntity person = registration.getPerson();
        JourneeRegistrationEntity at = (journee == null ? Optional.<JourneeRegistrationEntity>empty() : registration.journee(journee))
                .or(() -> registration.getJournees().stream().findFirst())
                .orElse(null);
        return new RosterEntryDto(person.getUserId(), person.getFirstName(), person.getLastName(), person.getPhone(),
                registration.getPoste(), at == null ? registration.getSignedUpAt() : at.getSignedUpAt(),
                GroupRefDto.of(registration.getGroup()), DemandeDto.of(registration),
                at == null ? null : personOf(at.getConducteur()), at == null ? null : personOf(at.getPassager()),
                registration.isChefOfItsGroup(), registration.journeeDates(),
                forBureau ? person.getEmail() : null, forBureau ? person.getBirthDate() : null,
                forBureau ? DechargeDto.of(registration) : null);
    }

    /** true when the Event has no maximum or still has {@code needed} places under it that Journée (every Poste counts). */
    private boolean placeLeft(EventEntity event, LocalDate journee, int needed) {
        return event.getMaxParticipants() == null
                || journeeRepository.countParticipants(event.getEventId(), journee) + needed <= event.getMaxParticipants();
    }

    /** Sign-up is open while Ouvert or Complet, to someone with a phone number. */
    private static void requireSignUpOpenTo(EventEntity event, UserEntity person) {
        refuseInClosedSector(event);
        // The Super admin is a dedicated account: its holder rides through their own (ADR 0006)
        if (person.getRole() == RoleEnum.SUPER_ADMIN)
            throw new InvalidResourceException("The Super admin account signs up for no Event.");
        // From Membre up, a person rides only at their own Secteur's Events (ADR 0004)
        if (person.getSector() != null && !person.belongsTo(event.getSector()))
            throw new InvalidResourceException("This Event is reserved to its Secteur's Membres and to Bénévoles.");
        EventStatusEnum current = event.currentStatus();
        if (!current.acceptsSignUps())
            throw new InvalidResourceException("Sign-up is closed for an Event in " + current.id());
        if (!person.hasPhone())
            throw new PhoneRequiredException("A phone number is required to sign up: " + person.getUserId());
    }

    private static NestedUserDto personRef(UserEntity person) {
        return new NestedUserDto(person.getUserId(), person.getFirstName(), person.getLastName());
    }

    /** The person of the other sign-up of a pair, if any. */
    private static NestedUserDto personOf(JourneeRegistrationEntity journee) {
        return journee == null ? null : personRef(journee.getRegistration().getPerson());
    }

    private static void refuseOnFinal(EventEntity event) {
        refuseInClosedSector(event);
        if (event.currentStatus().isFinal())
            throw new InvalidResourceException("An archived or cancelled Event is no longer edited.");
    }

    private void refuseOnArchived(EventEntity event) {
        refuseInClosedSector(event);
        if (event.currentStatus() == EventStatusEnum.ARCHIVE)
            throw new InvalidResourceException("An archived Event keeps its roster.");
    }

    /** A Secteur fermé is read-only: its Events and their sign-ups are kept as they were. */
    private static void refuseInClosedSector(EventEntity event) {
        if (event.isInClosedSector())
            throw new InvalidResourceException("The Event's Secteur is fermé: " + event.getEventId());
    }

    /** An Event of a Secteur fermé reads as not found for everyone but the Super admin. */
    private boolean visible(EventEntity event) {
        return !event.isInClosedSector() || viewer.seesClosedSecteurs();
    }

    @Override
    @Transactional
    public void doCloseEventsOfSector(UUID sectorId) {
        eventRepository.list("sector.sectorId", sectorId).forEach(event -> {
            EventStatusEnum current = event.currentStatus();
            // An Event that took place (or is taking place) is Archivé; one that never happened is Annulé
            event.setStatus(current == EventStatusEnum.EN_COURS || current == EventStatusEnum.ARCHIVE
                    ? EventStatusEnum.ARCHIVE
                    : EventStatusEnum.ANNULE);
        });
    }

    private EventRegistrationEntity registrationOrThrow(UUID eventId, UUID personId) {
        return registrationRepository.findByEventAndPerson(eventId, personId)
                .orElseThrow(() -> new UnknownResourceException("No sign-up of " + personId + " for " + eventId));
    }

    /** Locks the Event so concurrent sign-ups and moves up cannot both take the last place. */
    private EventEntity lockedEventOrThrow(UUID eventId) {
        return eventRepository.findByIdOptional(eventId, LockModeType.PESSIMISTIC_WRITE)
                .filter(this::visible)
                .orElseThrow(() -> new UnknownResourceException("Event not found: " + eventId));
    }

    private static RegistrationDto toDto(EventRegistrationEntity registration) {
        List<RegistrationDto.JourneeDto> journees = registration.getJournees().stream()
                .map(j -> new RegistrationDto.JourneeDto(j.getJournee(), j.status(), personOf(j.getConducteur()), personOf(j.getPassager())))
                .toList();
        NestedUserDto conducteur = journees.stream().map(RegistrationDto.JourneeDto::conducteur).filter(Objects::nonNull).findFirst().orElse(null);
        return new RegistrationDto(registration.getEvent().getEventId(), registration.getPoste(), registration.status(),
                registration.getSignedUpAt(), GroupRefDto.of(registration.getGroup()), DemandeDto.of(registration), conducteur,
                registration.isChefOfItsGroup(), journees, DechargeDto.of(registration));
    }

    /** A maximum of 0 or less means no maximum. */
    private static void normalizeMaximum(EventEntity event) {
        if (event.getMaxParticipants() != null && event.getMaxParticipants() <= 0)
            event.setMaxParticipants(null);
    }

    private EventEntity eventOrThrow(UUID eventId) {
        if (eventId == null) throw new InvalidResourceException("Missing event id.");
        return eventRepository.findByIdOptional(eventId)
                .filter(this::visible)
                .orElseThrow(() -> new UnknownResourceException("Event not found: " + eventId));
    }

    /** An Event has a name, a start and an end, in that order. */
    private static void validate(EventEntity event) {
        if (event.getName() == null || event.getName().isBlank())
            throw new InvalidResourceException("An Event has a name.");
        if (event.getStartDateTime() == null || event.getEndDateTime() == null)
            throw new InvalidResourceException("An Event has a start and an end.");
        if (event.getEndDateTime().isBefore(event.getStartDateTime()))
            throw new InvalidResourceException("An Event cannot end before it starts.");
    }
}
