import { JsonStore } from "./jsonStore";
import { createEventsApi } from "./fakeEvents";
import { Group } from "../../features/groups/types";
import { acceptsSignUps, EventStatus } from "../../features/events/status";
import {
  Actor,
  Demande,
  DemandeStatus,
  GroupRef,
  GroupRoster,
  MonGroupe,
  PersonRef,
  PhoneRequiredError,
  Registration,
  Poste,
  Roster,
  RosterEntry,
  SigningUpPerson,
  groupKindOf,
} from "../../features/events/registrationsApi";

const ROSTERS_KEY = "urue-event-rosters";
const MAXIMUMS_KEY = "urue-event-group-maximums";

interface StoredEntry {
  personId: string;
  firstName: string;
  lastName: string;
  phone: string;
  poste: Poste;
  signedUpAt: string;
  waiting: boolean;
  /** Pour un passager : son conducteur, qu'il suit partout. */
  conducteurId?: string | null;
  groupId?: string | null;
  demandeGroupId?: string | null;
  demandeStatus?: DemandeStatus | null;
}

/**
 * Client mocké, mêmes contrats que EventController : inscription (PUT/DELETE
 * /api/events/{eventId}/registration, ?groupId= pour une Demande de groupe,
 * ?conducteurId= pour un passager), conducteurs inscrits (GET .../conducteurs),
 * nouvelle Demande (PUT .../registration/demande), décision (POST
 * .../demandes/{personId}/accept|refuse), liste du Bureau (GET .../roster,
 * POST .../roster/{personId}/promote, DELETE .../roster/{personId}, PUT et
 * DELETE .../roster/{personId}/group), maximum d'un Groupe (PUT
 * .../groups/{groupId}/maximum) et Mon groupe (GET /api/events/mon-groupe).
 * Une seule liste par Événement alimente toutes ces vues.
 */
export function createRegistrationsApi(store: JsonStore, events: ReturnType<typeof createEventsApi>, groupsNow: () => Group[]) {
  const groups = { fetchGroups: async () => groupsNow() };
  const readAll = () => store.read<Record<string, StoredEntry[]>>(ROSTERS_KEY, {});
  const read = (eventId: string) => readAll()[eventId] ?? [];
  const write = (eventId: string, entries: StoredEntry[]) => store.write(ROSTERS_KEY, { ...readAll(), [eventId]: entries });
  const update = (eventId: string, personId: string, patch: Partial<StoredEntry>) =>
    write(eventId, read(eventId).map((e) => (e.personId === personId ? { ...e, ...patch } : e)));
  const readAllMaximums = () => store.read<Record<string, Record<string, number>>>(MAXIMUMS_KEY, {});
  const maximumsOf = (eventId: string) => readAllMaximums()[eventId] ?? {};

  const ref = (all: Group[], groupId: string | null | undefined): GroupRef | null => {
    const group = all.find((g) => g.groupId === groupId);
    return group ? { groupId: group.groupId, name: group.name } : null;
  };

  const personRef = ({ personId, firstName, lastName }: StoredEntry): PersonRef => ({ userId: personId, firstName, lastName });

  const conducteurRef = (eventId: string, conducteurId: string | null | undefined): PersonRef | null => {
    const conducteur = read(eventId).find((e) => e.personId === conducteurId);
    return conducteur ? personRef(conducteur) : null;
  };

  const toEntry = (
    all: Group[],
    eventId: string,
    { waiting: _waiting, groupId, demandeGroupId, demandeStatus, conducteurId, ...entry }: StoredEntry
  ): RosterEntry => {
    const demandeGroup = ref(all, demandeGroupId);
    return {
      ...entry,
      group: ref(all, groupId),
      demande: demandeStatus && demandeGroup ? { group: demandeGroup, status: demandeStatus } : null,
      conducteur: conducteurRef(eventId, conducteurId),
      passagers: passagersOf(eventId, entry.personId).length,
      leadsGroup: leads(all, groupId, entry.personId),
    };
  };

  /** Comme EventRegistrationEntity.isChefOfItsGroup : on mène le Groupe avec lequel on roule (ADR 0010). */
  const leads = (all: Group[], groupId: string | null | undefined, personId: string) =>
    Boolean(groupId) && all.find((g) => g.groupId === groupId)?.chef?.userId === personId;
  const refuseOnChef = async (entry: StoredEntry) => {
    if (leads(await groups.fetchGroups(), entry.groupId, entry.personId))
      throw new Error("Le Chef du groupe y roule avec lui : changez l'Affectation.");
  };

  const toRegistration = async (eventId: string, entry: StoredEntry): Promise<Registration> => {
    const { group, demande, conducteur, leadsGroup } = toEntry(await groups.fetchGroups(), eventId, entry);
    return { eventId, poste: entry.poste, status: entry.waiting ? "en_attente" : "participant", signedUpAt: entry.signedUpAt, group, demande, conducteur, leadsGroup };
  };

  /** Les passagers d'un conducteur, qui suivent chaque changement de sa place et de son Groupe. */
  const passagersOf = (eventId: string, conducteurId: string) => read(eventId).filter((e) => e.conducteurId === conducteurId);
  /** Places que prennent un conducteur et ses passagers face à un maximum. */
  const placesTaken = (eventId: string, personId: string) => 1 + passagersOf(eventId, personId).length;
  /** Applique un changement au conducteur et à ses passagers. */
  const updateWithPassagers = (eventId: string, personId: string, patch: Partial<StoredEntry>, passagerPatch: Partial<StoredEntry> = patch) =>
    write(
      eventId,
      read(eventId).map((e) =>
        e.personId === personId ? { ...e, ...patch } : e.conducteurId === personId ? { ...e, ...passagerPatch } : e
      )
    );
  const requireNotPassager = (entry: StoredEntry) => {
    if (entry.poste === "passager") throw new Error("Un passager suit son conducteur : agissez sur le conducteur.");
  };

  const eventOrThrow = async (eventId: string) => {
    const event = await events.fetchEventById(eventId, true);
    if (!event) throw new Error("Événement introuvable.");
    return event;
  };

  const entryOrThrow = (eventId: string, personId: string) => {
    const entry = read(eventId).find((e) => e.personId === personId);
    if (!entry) throw new Error("Aucune inscription à cet événement.");
    return entry;
  };

  const groupOrThrow = async (groupId: string) => {
    const group = (await groups.fetchGroups()).find((g) => g.groupId === groupId);
    if (!group) throw new Error("Groupe introuvable.");
    return group;
  };

  /** Comme EventRegistrationEntity.requireFits : seuls ceux dont le Poste va avec sa sorte sont avec un Groupe. */
  const requireFits = (poste: Poste, group: Group) => {
    if (groupKindOf(poste) !== group.kind)
      throw new Error(poste === "point_fixe" ? "Un point fixe n'est avec aucun groupe." : "Ce groupe n'est pas de la sorte qui va avec ce poste.");
  };

  /** Un Chef n'agit que sur le Groupe qu'il mène ; le Bureau sur tous. */
  const requireLeaderOrBureau = async (actor: Actor, groupId: string | null | undefined) => {
    const group = (await groups.fetchGroups()).find((g) => g.groupId === groupId);
    if (!actor.bureau && group?.chef?.userId !== actor.personId) throw new Error("Vous ne menez pas ce groupe.");
  };

  /** Il reste `needed` places sous le maximum de l'Événement (les passagers comptent). */
  const placeLeft = (eventId: string, max: number | null | undefined, needed = 1) =>
    !max || read(eventId).filter((e) => !e.waiting).length + needed <= max;

  const bySignUp = (a: StoredEntry, b: StoredEntry) => a.signedUpAt.localeCompare(b.signedUpAt);

  const ridesWith = (groupId: string) => (e: StoredEntry) => e.groupId === groupId;
  /** Demande en attente pour ce Groupe : sa Liste d'attente une fois le maximum atteint. */
  const asksFor = (groupId: string) => (e: StoredEntry) => e.demandeGroupId === groupId && e.demandeStatus === "en_attente";

  /** Au-delà du maximum du Groupe, personne de plus n'y entre ; sans maximum, pas de limite. */
  const requireRoomIn = (eventId: string, groupId: string, needed = 1) => {
    const maximum = maximumsOf(eventId)[groupId];
    if (maximum && read(eventId).filter(ridesWith(groupId)).length + needed > maximum)
      throw new Error(`Le groupe a atteint son maximum de ${maximum} pour cet événement.`);
  };

  const fetchRoster = async (eventId: string): Promise<Roster> => {
    const event = await eventOrThrow(eventId);
    const all = await groups.fetchGroups();
    const entries = [...read(eventId)].sort(bySignUp);
    const maximums = maximumsOf(eventId);
    return {
      eventId,
      maxParticipants: event.maxParticipants ?? null,
      participants: entries.filter((e) => !e.waiting).map((e) => toEntry(all, eventId, e)),
      waiting: entries.filter((e) => e.waiting).map((e) => toEntry(all, eventId, e)),
      groups: all
        .filter((g) => g.sectorId === event.sectorId)
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((g) => ({
          group: { groupId: g.groupId, name: g.name },
          maximum: maximums[g.groupId] ?? null,
          members: entries.filter(ridesWith(g.groupId)).map((e) => toEntry(all, eventId, e)),
          demandes: entries.filter(asksFor(g.groupId)).map((e) => toEntry(all, eventId, e)),
        })),
    };
  };

  /** Un Événement archivé garde ses inscrits. */
  const refuseOnArchived = async (eventId: string) => {
    if ((await eventOrThrow(eventId)).status === "archive") throw new Error("Un événement archivé garde ses inscrits.");
  };

  const requestGroup = async (eventId: string, personId: string, groupId: string): Promise<Registration> => {
    await refuseOnArchived(eventId);
    const entry = entryOrThrow(eventId, personId);
    if (entry.poste === "passager") throw new Error("Un passager roule avec le groupe de son conducteur.");
    if (entry.groupId) throw new Error("Vous roulez déjà avec un groupe à cet événement.");
    requireFits(entry.poste, await groupOrThrow(groupId));
    update(eventId, personId, { demandeGroupId: groupId, demandeStatus: "en_attente" });
    return toRegistration(eventId, entryOrThrow(eventId, personId));
  };

  return {
    fetchMyRegistrations: async (personId: string): Promise<Registration[]> =>
      Promise.all(
        Object.entries(readAll()).flatMap(([eventId, entries]) =>
          entries.filter((e) => e.personId === personId).map((e) => toRegistration(eventId, e))
        )
      ),

    /** En conducteur, `groupId` fait une Demande de groupe ; avec `conducteurId`, en passager de ce conducteur, dont on prend la place et le Groupe. */
    signUp: async (eventId: string, person: SigningUpPerson, groupId?: string, conducteurId?: string, poste: Poste = conducteurId ? "passager" : "conducteur"): Promise<Registration> => {
      const existing = read(eventId).find((e) => e.personId === person.userId);
      if (existing && conducteurId && existing.conducteurId !== conducteurId) throw new Error("Vous êtes déjà inscrit à cet événement.");
      if (existing) return toRegistration(eventId, existing);

      const event = await eventOrThrow(eventId);
      if (!acceptsSignUps(event.status)) throw new Error("Les inscriptions sont fermées pour cet événement.");
      if (person.sectorId && person.sectorId !== event.sectorId)
        throw new Error("Cet événement est réservé aux membres de son secteur et aux bénévoles.");
      if (!person.phone?.trim()) throw new PhoneRequiredError();
      if ((poste === "passager") !== Boolean(conducteurId)) throw new Error("Un passager, et lui seul, nomme son conducteur.");
      if (groupId) requireFits(poste, await groupOrThrow(groupId));

      const conducteur = conducteurId ? read(eventId).find((e) => e.personId === conducteurId) : undefined;
      if (conducteurId) {
        if (groupId) throw new Error("Un passager roule avec le groupe de son conducteur.");
        if (!conducteur || conducteur.poste !== "conducteur") throw new Error("Choisissez un conducteur inscrit à cet événement.");
        // Le passager prend la place du conducteur : refusé plutôt que séparé s'il n'y a plus de place, y compris « Complet »
        if (!conducteur.waiting) {
          if (event.status !== "ouvert" || !placeLeft(eventId, event.maxParticipants)) throw new Error("Plus de place à côté de ce conducteur.");
          if (conducteur.groupId) requireRoomIn(eventId, conducteur.groupId);
        }
      }

      const entry: StoredEntry = {
        personId: person.userId,
        firstName: person.firstName,
        lastName: person.lastName,
        phone: person.phone.trim(),
        poste,
        // Horodatage strictement croissant : l'ordre d'inscription départage la liste d'attente
        signedUpAt: new Date(Math.max(Date.now(), ...read(eventId).map((e) => Date.parse(e.signedUpAt) + 1))).toISOString(),
        waiting: conducteur ? conducteur.waiting : !(event.status === "ouvert" && placeLeft(eventId, event.maxParticipants)),
        conducteurId: conducteur?.personId ?? null,
        groupId: conducteur?.groupId ?? null,
        demandeGroupId: groupId ?? null,
        demandeStatus: groupId ? "en_attente" : null,
      };
      write(eventId, [...read(eventId), entry]);
      return toRegistration(eventId, entry);
    },

    /** Se désinscrire ; un conducteur emmène ses passagers. */
    withdraw: async (eventId: string, personId: string): Promise<void> => {
      await refuseOnChef(entryOrThrow(eventId, personId));
      await refuseOnArchived(eventId);
      write(eventId, read(eventId).filter((e) => e.personId !== personId && e.conducteurId !== personId));
    },

    fetchConducteurs: async (eventId: string): Promise<PersonRef[]> =>
      [...read(eventId)].sort(bySignUp).filter((e) => e.poste === "conducteur").map(personRef),

    /** Comme EventServiceImpl.changePoste : jamais en passager, ni pour le Chef, ni pour un conducteur qui a un passager. */
    changePoste: async (eventId: string, personId: string, poste: Poste): Promise<Roster> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      await refuseOnChef(entry);
      if (poste === "passager") throw new Error("Un passager nomme son conducteur en s'inscrivant.");
      if (passagersOf(eventId, personId).length > 0 && poste !== "conducteur") throw new Error("Un conducteur qui a un passager garde son poste.");
      const all = await groups.fetchGroups();
      const kindOf = (groupId: string | null | undefined) => all.find((g) => g.groupId === groupId)?.kind;
      const patch: Partial<StoredEntry> = { poste, conducteurId: null };
      // Un Groupe (ou une Demande en attente) d'une autre sorte que celle du nouveau Poste est quitté
      if (entry.groupId && kindOf(entry.groupId) !== groupKindOf(poste)) Object.assign(patch, { groupId: null, demandeGroupId: null, demandeStatus: null });
      else if (!entry.groupId && entry.demandeStatus === "en_attente" && kindOf(entry.demandeGroupId) !== groupKindOf(poste))
        Object.assign(patch, { demandeGroupId: null, demandeStatus: null });
      update(eventId, personId, patch);
      return fetchRoster(eventId);
    },

    /** Comme GroupKindChange : quelqu'un d'autre que son Chef est avec le Groupe, ou le demande, à un Événement. */
    concernsOthersThanChef: (group: Group): boolean =>
      Object.values(readAll()).some((entries) =>
        entries.some((e) => (ridesWith(group.groupId)(e) || asksFor(group.groupId)(e)) && e.personId !== group.chef?.userId)
      ),

    /** Le Chef prend le Poste de la nouvelle sorte de son Groupe (ADR 0010). */
    chefFollowsKind: (group: Group) =>
      Object.entries(readAll()).forEach(([eventId, entries]) =>
        entries
          .filter((e) => ridesWith(group.groupId)(e) && e.personId === group.chef?.userId)
          .forEach((e) => update(eventId, e.personId, { poste: group.kind === "service" ? "service" : "conducteur" }))
      ),

    requestGroup,

    decideDemande: async (eventId: string, personId: string, actor: Actor, accept: boolean): Promise<Registration> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      if (entry.demandeStatus !== "en_attente") throw new Error("Aucune Demande de groupe en attente.");
      await requireLeaderOrBureau(actor, entry.demandeGroupId);
      // Au-delà du maximum, la Demande reste en attente, sur la Liste d'attente du Groupe
      if (accept && entry.demandeGroupId) requireRoomIn(eventId, entry.demandeGroupId, placesTaken(eventId, personId));
      if (accept) updateWithPassagers(eventId, personId, { groupId: entry.demandeGroupId, demandeStatus: "acceptee" }, { groupId: entry.demandeGroupId });
      else update(eventId, personId, { demandeStatus: "refusee" });
      return toRegistration(eventId, entryOrThrow(eventId, personId));
    },

    placeInGroup: async (eventId: string, personId: string, groupId: string): Promise<Roster> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      requireNotPassager(entry);
      await refuseOnChef(entry);
      if (entry.waiting) throw new Error("Seul un Participant est placé dans un groupe.");
      requireFits(entry.poste, await groupOrThrow(groupId));
      if (entry.groupId !== groupId) requireRoomIn(eventId, groupId, placesTaken(eventId, personId));
      updateWithPassagers(eventId, personId, { groupId, demandeGroupId: groupId, demandeStatus: "acceptee" }, { groupId });
      return fetchRoster(eventId);
    },

    /** Le maximum d'un Groupe du Secteur de l'Événement ; null ou 0 le retire. */
    setGroupMaximum: async (eventId: string, groupId: string, maximum: number | null): Promise<Roster> => {
      const event = await eventOrThrow(eventId);
      if (event.status === "archive" || event.status === "annule") throw new Error("Un événement archivé ou annulé n'est plus modifié.");
      if ((await groupOrThrow(groupId)).sectorId !== event.sectorId) throw new Error("Ce groupe n'est pas du secteur de l'événement.");
      const { [groupId]: _previous, ...others } = maximumsOf(eventId);
      store.write(MAXIMUMS_KEY, { ...readAllMaximums(), [eventId]: maximum && maximum > 0 ? { ...others, [groupId]: maximum } : others });
      return fetchRoster(eventId);
    },

    takeOutOfGroup: async (eventId: string, personId: string, actor: Actor): Promise<Registration> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      if (!entry.groupId) throw new Error("Cette personne ne roule avec aucun groupe.");
      await requireLeaderOrBureau(actor, entry.groupId);
      requireNotPassager(entry);
      await refuseOnChef(entry);
      updateWithPassagers(eventId, personId, { groupId: null, demandeGroupId: null, demandeStatus: null }, { groupId: null });
      return toRegistration(eventId, entryOrThrow(eventId, personId));
    },

    fetchMonGroupe: async (chefId: string): Promise<MonGroupe> => {
      const all = await groups.fetchGroups();
      const led = all.find((g) => g.chef?.userId === chefId);
      if (!led) return { group: null, events: [] };

      const concerns = (e: StoredEntry) => ridesWith(led.groupId)(e) || asksFor(led.groupId)(e);
      // Tous les Événements du Secteur que le Chef prépare, même sans personne encore
      const eventsOfSecteur = (await events.fetchEvents(true)).filter(
        (e) => e.sectorId === led.sectorId && (acceptsSignUps(e.status) || e.status === "en_cours")
      );
      const withEntries = await Promise.all(
        Object.entries(readAll())
          .filter(([, entries]) => entries.some(concerns))
          .map(async ([eventId]) => events.fetchEventById(eventId, true))
      );
      const concerned = [...eventsOfSecteur, ...withEntries.filter((e) => e && !eventsOfSecteur.some((s) => s.eventId === e.eventId))];
      const rosters = concerned.map((event) => ({ event, entries: event ? read(event.eventId) : [] }));
      return {
        group: { groupId: led.groupId, name: led.name },
        events: rosters
          .filter(({ event }) => event && event.status !== "archive")
          .map(({ event, entries }) => ({
            eventId: event!.eventId,
            name: event!.name,
            startDateTime: event!.startDateTime,
            status: event!.status,
            maximum: maximumsOf(event!.eventId)[led.groupId] ?? null,
            members: [...entries].sort(bySignUp).filter(ridesWith(led.groupId)).map((e) => toEntry(all, event!.eventId, e)),
            demandes: [...entries].sort(bySignUp).filter(asksFor(led.groupId)).map((e) => toEntry(all, event!.eventId, e)),
          }))
          .sort((a, b) => a.startDateTime.localeCompare(b.startDateTime)),
      };
    },

    fetchRoster,

    promote: async (eventId: string, personId: string): Promise<Roster> => {
      const event = await eventOrThrow(eventId);
      const entry = read(eventId).find((e) => e.personId === personId);
      if (entry) requireNotPassager(entry);
      if (!entry?.waiting) throw new Error("Cette personne n'est pas sur la liste d'attente.");
      await refuseOnArchived(eventId);
      if (!placeLeft(eventId, event.maxParticipants, placesTaken(eventId, personId)))
        throw new Error("L'événement a atteint son maximum de participants.");
      updateWithPassagers(eventId, personId, { waiting: false });
      return fetchRoster(eventId);
    },

    remove: async (eventId: string, personId: string): Promise<Roster> => {
      await refuseOnChef(entryOrThrow(eventId, personId));
      await refuseOnArchived(eventId);
      write(eventId, read(eventId).filter((e) => e.personId !== personId && e.conducteurId !== personId));
      return fetchRoster(eventId);
    },
  };
}
