import { JsonStore } from "./jsonStore";
import { createEventsApi } from "./fakeEvents";
import { Group } from "../../features/groups/types";
import { acceptsSignUps, EventStatus } from "../../features/events/status";
import {
  Actor,
  ConducteurNotFoundError,
  DemandeStatus,
  GroupRef,
  MonGroupe,
  PersonRef,
  PhoneRequiredError,
  Poste,
  Registration,
  Roster,
  RosterEntry,
  SigningUpPerson,
  groupKindOf,
} from "../../features/events/registrationsApi";
import { eventJournees, Journee } from "../../lib/journees";

/** Comme NameMatching : sans accents, majuscules ni ponctuation, prénom et nom dans un ordre ou l'autre. */
const normalized = (name: string) =>
  name.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
const distance = (a: string, b: string) => {
  let previous = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++)
      current.push(Math.min(previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1), previous[j] + 1, current[j - 1] + 1));
    previous = current;
  }
  return previous[b.length];
};
const ratio = (a: string, b: string) => 1 - distance(a, b) / Math.max(a.length, b.length, 1);
const likeness = (typed: string, firstName: string, lastName: string) => {
  const wanted = normalized(typed);
  if (!wanted) return 0;
  return Math.max(ratio(wanted, normalized(`${firstName} ${lastName}`)), ratio(wanted, normalized(`${lastName} ${firstName}`)));
};

const ROSTERS_KEY = "urue-event-rosters-v2";
const MAXIMUMS_KEY = "urue-event-group-maximums";

/** Une Journée à laquelle on est inscrit : sa place, son ordre, et pour un passager son conducteur ce jour-là. */
interface StoredJournee {
  journee: Journee;
  waiting: boolean;
  signedUpAt: string;
  conducteurId?: string | null;
}

/** Comme EventRegistrationEntity : Poste, Groupe et Demande pour tout l'Événement, et chaque Journée (ADR 0011). */
interface StoredEntry {
  personId: string;
  firstName: string;
  lastName: string;
  phone: string;
  poste: Poste;
  signedUpAt: string;
  groupId?: string | null;
  demandeGroupId?: string | null;
  demandeStatus?: DemandeStatus | null;
  journees: StoredJournee[];
}

/**
 * Client mocké, mêmes contrats que EventController : inscription par Journée (PUT/DELETE
 * /api/events/{eventId}/registration, ?poste=, ?journee=, ?groupId= pour une Demande de groupe, ?conducteurId= pour
 * un passager qui vient avec un conducteur, ?withPassager=), conducteurs inscrits (GET .../conducteurs), nouvelle
 * Demande (PUT .../registration/demande), décision (POST .../demandes/{personId}/accept|refuse), liste du Bureau par
 * Journée (GET .../roster?journee=, POST .../roster/{personId}/promote, DELETE .../roster/{personId}, PUT et DELETE
 * .../roster/{personId}/group, PUT .../roster/{personId}/poste), binômes (PUT et DELETE
 * .../roster/{personId}/journees/{journee}/conducteur), maximum d'un Groupe (PUT .../groups/{groupId}/maximum) et
 * Mon groupe (GET /api/events/mon-groupe). Une seule liste par Événement alimente toutes ces vues.
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

  /** Horodatage strictement croissant : l'ordre d'inscription départage chaque Liste d'attente. */
  const now = (eventId: string) =>
    new Date(
      Math.max(Date.now(), ...read(eventId).flatMap((e) => [e.signedUpAt, ...e.journees.map((j) => j.signedUpAt)].map((t) => Date.parse(t) + 1)))
    ).toISOString();

  const ref = (all: Group[], groupId: string | null | undefined): GroupRef | null => {
    const group = all.find((g) => g.groupId === groupId);
    return group ? { groupId: group.groupId, name: group.name } : null;
  };

  const personRef = ({ personId, firstName, lastName }: StoredEntry): PersonRef => ({ userId: personId, firstName, lastName });
  const journeeOf = (entry: StoredEntry, journee: Journee) => entry.journees.find((j) => j.journee === journee);
  /** Le passager derrière ce conducteur ce jour-là. */
  const passagerOf = (eventId: string, conducteurId: string, journee: Journee) =>
    read(eventId).find((e) => journeeOf(e, journee)?.conducteurId === conducteurId);
  const conducteurOn = (eventId: string, entry: StoredEntry, journee: Journee) => {
    const conducteurId = journeeOf(entry, journee)?.conducteurId;
    return conducteurId ? read(eventId).find((e) => e.personId === conducteurId) : undefined;
  };

  /** Comme EventServiceImpl.toRosterEntry : la place et le binôme de cette Journée (la première de la personne sinon). */
  const toEntry = (all: Group[], eventId: string, entry: StoredEntry, journee?: Journee): RosterEntry => {
    const at = (journee && journeeOf(entry, journee)) || entry.journees[0];
    const demandeGroup = ref(all, entry.demandeGroupId);
    const conducteur = at ? conducteurOn(eventId, entry, at.journee) : undefined;
    const passager = at ? passagerOf(eventId, entry.personId, at.journee) : undefined;
    return {
      personId: entry.personId,
      firstName: entry.firstName,
      lastName: entry.lastName,
      phone: entry.phone,
      poste: entry.poste,
      signedUpAt: at?.signedUpAt ?? entry.signedUpAt,
      group: ref(all, entry.groupId),
      demande: entry.demandeStatus && demandeGroup ? { group: demandeGroup, status: entry.demandeStatus } : null,
      conducteur: conducteur ? personRef(conducteur) : null,
      passager: passager ? personRef(passager) : null,
      leadsGroup: leads(all, entry.groupId, entry.personId),
      journees: entry.journees.map((j) => j.journee).sort(),
    };
  };

  /** Comme EventRegistrationEntity.isChefOfItsGroup : on mène le Groupe avec lequel on roule (ADR 0010). */
  const leads = (all: Group[], groupId: string | null | undefined, personId: string) =>
    Boolean(groupId) && all.find((g) => g.groupId === groupId)?.chef?.userId === personId;
  const refuseOnChef = async (entry: StoredEntry) => {
    if (leads(await groups.fetchGroups(), entry.groupId, entry.personId))
      throw new Error("Le Chef du groupe y roule avec lui : changez l'Affectation.");
  };

  /** Comme EventServiceImpl.toDto. */
  const toRegistration = async (eventId: string, entry: StoredEntry): Promise<Registration> => {
    const all = await groups.fetchGroups();
    const { group, demande, leadsGroup } = toEntry(all, eventId, entry);
    const journees = [...entry.journees]
      .sort((a, b) => a.journee.localeCompare(b.journee))
      .map((j) => {
        const conducteur = conducteurOn(eventId, entry, j.journee);
        const passager = passagerOf(eventId, entry.personId, j.journee);
        return {
          journee: j.journee,
          status: j.waiting ? ("en_attente" as const) : ("participant" as const),
          conducteur: conducteur ? personRef(conducteur) : null,
          passager: passager ? personRef(passager) : null,
        };
      });
    return {
      eventId,
      poste: entry.poste,
      status: entry.journees.every((j) => j.waiting) ? "en_attente" : "participant",
      signedUpAt: entry.signedUpAt,
      group,
      demande,
      conducteur: journees.find((j) => j.conducteur)?.conducteur ?? null,
      leadsGroup,
      journees,
    };
  };

  const eventOrThrow = async (eventId: string) => {
    const event = await events.fetchEventById(eventId, true);
    if (!event) throw new Error("Événement introuvable.");
    return event;
  };
  const journeesOfEvent = async (eventId: string) => {
    const event = await eventOrThrow(eventId);
    return eventJournees(event.startDateTime, event.endDateTime);
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

  /** Il reste `needed` places sous le maximum de l'Événement cette Journée (chaque Poste compte). */
  const placeLeft = (eventId: string, max: number | null | undefined, journee: Journee, needed = 1) =>
    !max || read(eventId).filter((e) => journeeOf(e, journee) && !journeeOf(e, journee)!.waiting).length + needed <= max;

  const ridesWith = (groupId: string) => (e: StoredEntry) => e.groupId === groupId;
  /** Demande en attente pour ce Groupe : sa Liste d'attente une fois le maximum atteint. */
  const asksFor = (groupId: string) => (e: StoredEntry) => e.demandeGroupId === groupId && e.demandeStatus === "en_attente";

  /** Au-delà du maximum du Groupe l'une de ces Journées, personne de plus n'y entre ; sans maximum, pas de limite. */
  const requireRoomIn = (eventId: string, groupId: string, journees: Journee[], needed = 1) => {
    const maximum = maximumsOf(eventId)[groupId];
    if (!maximum) return;
    journees.forEach((journee) => {
      if (read(eventId).filter((e) => ridesWith(groupId)(e) && journeeOf(e, journee)).length + needed > maximum)
        throw new Error(`Le groupe a atteint son maximum de ${maximum} pour cet événement.`);
    });
  };

  /** Ceux d'une Journée, dans l'ordre où ils s'y sont inscrits. */
  const at = (entries: StoredEntry[], journee: Journee) =>
    entries
      .filter((e) => journeeOf(e, journee))
      .sort((a, b) => journeeOf(a, journee)!.signedUpAt.localeCompare(journeeOf(b, journee)!.signedUpAt));

  const fetchRoster = async (eventId: string, asked?: Journee): Promise<Roster> => {
    const event = await eventOrThrow(eventId);
    const journees = eventJournees(event.startDateTime, event.endDateTime);
    const journee = asked ?? journees[0];
    const all = await groups.fetchGroups();
    const entries = read(eventId);
    const thatDay = at(entries, journee);
    const maximums = maximumsOf(eventId);
    return {
      eventId,
      journee,
      journees: journees.map((j) => {
        const day = entries.filter((e) => journeeOf(e, j));
        const waiting = day.filter((e) => journeeOf(e, j)!.waiting).length;
        return { journee: j, participants: day.length - waiting, waiting };
      }),
      maxParticipants: event.maxParticipants ?? null,
      participants: thatDay.filter((e) => !journeeOf(e, journee)!.waiting).map((e) => toEntry(all, eventId, e, journee)),
      waiting: thatDay.filter((e) => journeeOf(e, journee)!.waiting).map((e) => toEntry(all, eventId, e, journee)),
      groups: all
        .filter((g) => g.sectorId === event.sectorId)
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((g) => ({
          group: { groupId: g.groupId, name: g.name },
          maximum: maximums[g.groupId] ?? null,
          members: thatDay.filter(ridesWith(g.groupId)).map((e) => toEntry(all, eventId, e, journee)),
          demandes: thatDay.filter(asksFor(g.groupId)).map((e) => toEntry(all, eventId, e, journee)),
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
    if (entry.groupId) throw new Error("Vous roulez déjà avec un groupe à cet événement.");
    requireFits(entry.poste, await groupOrThrow(groupId));
    update(eventId, personId, { demandeGroupId: groupId, demandeStatus: "en_attente" });
    return toRegistration(eventId, entryOrThrow(eventId, personId));
  };

  /** Met fin aux binômes de ce conducteur (ou de ce passager) ces Journées-là. */
  const unpairOn = (eventId: string, personId: string, journees: Journee[]) =>
    write(
      eventId,
      read(eventId).map((e) => ({
        ...e,
        journees: e.journees.map((j) =>
          journees.includes(j.journee) && (j.conducteurId === personId || e.personId === personId) ? { ...j, conducteurId: null } : j
        ),
      }))
    );

  /** Comme EventServiceImpl.leave : quitter des Journées, avec ou sans le passager derrière soi ; sans Journée, on part. */
  const leave = (eventId: string, personId: string, journees: Journee[] | undefined, withPassager: boolean) => {
    const entry = entryOrThrow(eventId, personId);
    const leaving = journees?.length ? journees : entry.journees.map((j) => j.journee);
    leaving.forEach((j) => {
      if (!journeeOf(entry, j)) throw new Error("Pas d'inscription ce jour-là.");
    });
    const passagers = leaving.flatMap((j) => {
      const passager = passagerOf(eventId, personId, j);
      return passager ? [{ personId: passager.personId, journee: j }] : [];
    });
    unpairOn(eventId, personId, leaving);
    write(
      eventId,
      read(eventId)
        .map((e) => {
          if (e.personId === personId) return { ...e, journees: e.journees.filter((j) => !leaving.includes(j.journee)) };
          if (withPassager) return { ...e, journees: e.journees.filter((j) => !passagers.some((p) => p.personId === e.personId && p.journee === j.journee)) };
          return e;
        })
        .filter((e) => e.journees.length > 0)
    );
  };

  /** Les passagers derrière ce conducteur, quelle que soit la Journée. */
  const passagersOf = (eventId: string, conducteurId: string) =>
    read(eventId).filter((e) => e.journees.some((j) => j.conducteurId === conducteurId));

  return {
    fetchMyRegistrations: async (personId: string): Promise<Registration[]> =>
      Promise.all(
        Object.entries(readAll()).flatMap(([eventId, entries]) =>
          entries.filter((e) => e.personId === personId).map((e) => toRegistration(eventId, e))
        )
      ),

    /**
     * Aux Journées choisies (aucune : toutes), à un Poste ; `groupId` fait une Demande de groupe ; un passager qui
     * vient avec le conducteur dont le nom ressemble à `conducteurName` (comme NameMatching) roule derrière lui les
     * Journées où ce conducteur n'a pas encore de passager, et prend son Groupe ou sa Demande. S'inscrire de nouveau
     * au même Poste ajoute des Journées.
     */
    signUp: async (
      eventId: string,
      person: SigningUpPerson,
      groupId?: string,
      conducteurName?: string,
      poste: Poste = conducteurName ? "passager" : "conducteur",
      journees?: Journee[]
    ): Promise<Registration> => {
      const event = await eventOrThrow(eventId);
      const all = eventJournees(event.startDateTime, event.endDateTime);
      const chosen = journees?.length ? [...new Set(journees)].sort() : all;
      if (chosen.some((j) => !all.includes(j))) throw new Error("Cette date n'est pas une journée de l'événement.");
      if (conducteurName && poste !== "passager") throw new Error("Seul un passager nomme un conducteur.");
      if (conducteurName && groupId) throw new Error("Un passager qui vient avec un conducteur prend son groupe.");

      const existing = read(eventId).find((e) => e.personId === person.userId);
      if (existing && existing.poste !== poste) throw new Error("Vous êtes déjà inscrit à cet événement à un autre poste.");
      const added = existing ? chosen.filter((j) => !journeeOf(existing, j)) : chosen;
      if (existing && added.length === 0) return toRegistration(eventId, existing);

      if (!acceptsSignUps(event.status)) throw new Error("Les inscriptions sont fermées pour cet événement.");
      if (person.sectorId && person.sectorId !== event.sectorId)
        throw new Error("Cet événement est réservé aux membres de son secteur et aux bénévoles.");
      if (!person.phone?.trim()) throw new PhoneRequiredError();
      if (groupId && !existing) requireFits(poste, await groupOrThrow(groupId));

      const freeOn = (c: StoredEntry, j: Journee) => (journeeOf(c, j) && !passagerOf(eventId, c.personId, j) ? journeeOf(c, j) : undefined);
      // Comme EventServiceImpl.conducteurNamed : le nom tapé, rapproché des conducteurs libres l'une des Journées
      let conducteur: StoredEntry | undefined;
      if (conducteurName) {
        const scored = read(eventId)
          .filter((c) => c.poste === "conducteur" && c.personId !== person.userId && added.some((j) => freeOn(c, j)))
          .map((c) => ({ c, score: likeness(conducteurName, c.firstName, c.lastName) }))
          .filter(({ score }) => score >= 0.8)
          .sort((a, b) => b.score - a.score);
        if (scored.length === 0) throw new ConducteurNotFoundError(false);
        const exact = scored[0].score === 1 && (scored.length === 1 || scored[1].score < 1);
        if (!exact && scored.length > 1 && scored[0].score - scored[1].score < 0.1) throw new ConducteurNotFoundError(true);
        conducteur = scored[0].c;
      }
      const free = (j: Journee) => (conducteur ? freeOn(conducteur, j) : undefined);

      const ouvert = event.status === "ouvert";
      const stamp = now(eventId);
      const newJournees: StoredJournee[] = added.map((j) => {
        const behind = free(j);
        const left = ouvert && placeLeft(eventId, event.maxParticipants, j);
        // Refusé plutôt que séparé d'un conducteur confirmé quand il n'y a plus de place, y compris « Complet »
        if (behind && !behind.waiting && !left) throw new Error("Plus de place à côté de ce conducteur.");
        return { journee: j, waiting: behind ? behind.waiting : !left, signedUpAt: stamp, conducteurId: behind ? conducteur!.personId : null };
      });

      if (existing) {
        update(eventId, person.userId, { journees: [...existing.journees, ...newJournees] });
        return toRegistration(eventId, entryOrThrow(eventId, person.userId));
      }
      // « Je viens avec » : le Groupe (dans son maximum) ou la Demande du conducteur
      let comesWith: Partial<StoredEntry> = {};
      if (conducteur?.groupId) {
        requireRoomIn(eventId, conducteur.groupId, newJournees.filter((j) => !j.waiting).map((j) => j.journee));
        comesWith = { groupId: conducteur.groupId };
      } else if (conducteur?.demandeStatus === "en_attente") comesWith = { demandeGroupId: conducteur.demandeGroupId, demandeStatus: "en_attente" };
      const entry: StoredEntry = {
        personId: person.userId,
        firstName: person.firstName,
        lastName: person.lastName,
        phone: person.phone.trim(),
        poste,
        signedUpAt: stamp,
        groupId: null,
        demandeGroupId: groupId ?? null,
        demandeStatus: groupId ? "en_attente" : null,
        journees: newJournees,
        ...comesWith,
      };
      write(eventId, [...read(eventId), entry]);
      return toRegistration(eventId, entry);
    },

    /** Se retirer de Journées (aucune : toutes) ; `withPassager` : son passager se retire aussi ces Journées-là. */
    withdraw: async (eventId: string, personId: string, journees?: Journee[], withPassager = true): Promise<void> => {
      await refuseOnChef(entryOrThrow(eventId, personId));
      await refuseOnArchived(eventId);
      leave(eventId, personId, journees, withPassager);
    },

    /** Comme EventServiceImpl.changePoste : ni pour le Chef, ni pour un conducteur qui a un passager. */
    changePoste: async (eventId: string, personId: string, poste: Poste): Promise<Roster> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      await refuseOnChef(entry);
      if (passagersOf(eventId, personId).length > 0 && poste !== "conducteur") throw new Error("Un conducteur qui a un passager garde son poste.");
      if (poste !== "passager") unpairOn(eventId, personId, entry.journees.map((j) => j.journee));
      const all = await groups.fetchGroups();
      const kindOf = (groupId: string | null | undefined) => all.find((g) => g.groupId === groupId)?.kind;
      const patch: Partial<StoredEntry> = { poste };
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
      // Au-delà du maximum l'une de ses Journées, la Demande reste en attente, sur la Liste d'attente du Groupe
      if (accept && entry.demandeGroupId) requireRoomIn(eventId, entry.demandeGroupId, entry.journees.map((j) => j.journee));
      if (accept) update(eventId, personId, { groupId: entry.demandeGroupId, demandeStatus: "acceptee" });
      else update(eventId, personId, { demandeStatus: "refusee" });
      return toRegistration(eventId, entryOrThrow(eventId, personId));
    },

    /** `withPassager` : les passagers d'un conducteur le suivent, sinon leur binôme prend fin. */
    placeInGroup: async (eventId: string, personId: string, groupId: string, withPassager = true): Promise<Roster> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      await refuseOnChef(entry);
      if (entry.journees.every((j) => j.waiting)) throw new Error("Seul un Participant est placé dans un groupe.");
      requireFits(entry.poste, await groupOrThrow(groupId));
      const all = await groups.fetchGroups();
      const moving = [entry, ...(withPassager ? passagersOf(eventId, personId).filter((p) => !leads(all, p.groupId, p.personId)) : [])];
      if (!withPassager) unpairOn(eventId, personId, entry.journees.map((j) => j.journee));
      moving.filter((m) => m.groupId !== groupId).forEach((m) => requireRoomIn(eventId, groupId, m.journees.map((j) => j.journee)));
      moving.forEach((m) => update(eventId, m.personId, { groupId, demandeGroupId: groupId, demandeStatus: "acceptee" }));
      // Un passager ne roule que derrière un conducteur de son Groupe
      if (entry.poste === "passager")
        entry.journees.forEach((j) => {
          if (j.conducteurId && read(eventId).find((e) => e.personId === j.conducteurId)?.groupId !== groupId) unpairOn(eventId, personId, [j.journee]);
        });
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

    /** `withPassager` : les passagers d'un conducteur sortent aussi du Groupe, sinon leur binôme prend fin. */
    takeOutOfGroup: async (eventId: string, personId: string, actor: Actor, withPassager = true): Promise<Registration> => {
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      if (!entry.groupId) throw new Error("Cette personne ne roule avec aucun groupe.");
      await requireLeaderOrBureau(actor, entry.groupId);
      await refuseOnChef(entry);
      const all = await groups.fetchGroups();
      const leaving = [entry, ...(withPassager ? passagersOf(eventId, personId).filter((p) => p.groupId === entry.groupId && !leads(all, p.groupId, p.personId)) : [])];
      if (!withPassager) unpairOn(eventId, personId, entry.journees.map((j) => j.journee));
      leaving.forEach((m) => update(eventId, m.personId, { groupId: null, demandeGroupId: null, demandeStatus: null }));
      if (entry.poste === "passager") unpairOn(eventId, personId, entry.journees.map((j) => j.journee));
      return toRegistration(eventId, entryOrThrow(eventId, personId));
    },

    /** Le Bureau ou le Chef du passager l'associe à un conducteur de son Groupe, en attente ou confirmé comme lui. */
    pair: async (eventId: string, passagerId: string, journee: Journee, conducteurId: string, actor: Actor): Promise<Registration> => {
      await refuseOnArchived(eventId);
      const passager = entryOrThrow(eventId, passagerId);
      if (passager.poste !== "passager") throw new Error("Cette personne n'est pas passager.");
      await requireLeaderOrBureau(actor, passager.groupId);
      const conducteur = read(eventId).find((e) => e.personId === conducteurId && e.poste === "conducteur");
      if (!conducteur) throw new Error("Choisissez un conducteur inscrit à cet événement.");
      if ((conducteur.groupId ?? null) !== (passager.groupId ?? null)) throw new Error("Un passager roule derrière un conducteur de son groupe.");
      const mine = journeeOf(passager, journee);
      const theirs = journeeOf(conducteur, journee);
      if (!mine || !theirs) throw new Error("Pas d'inscription ce jour-là.");
      const taken = passagerOf(eventId, conducteurId, journee);
      if (taken && taken.personId !== passagerId) throw new Error("Ce conducteur a déjà un passager ce jour-là.");
      if (mine.waiting !== theirs.waiting) throw new Error("Un passager et son conducteur attendent, ou roulent, ensemble.");
      update(eventId, passagerId, { journees: passager.journees.map((j) => (j.journee === journee ? { ...j, conducteurId } : j)) });
      return toRegistration(eventId, entryOrThrow(eventId, passagerId));
    },

    /** Le Bureau ou le Chef du passager met fin à son binôme ce jour-là. */
    unpair: async (eventId: string, passagerId: string, journee: Journee, actor: Actor): Promise<Registration> => {
      await refuseOnArchived(eventId);
      const passager = entryOrThrow(eventId, passagerId);
      if (passager.poste !== "passager") throw new Error("Cette personne n'est pas passager.");
      await requireLeaderOrBureau(actor, passager.groupId);
      unpairOn(eventId, passagerId, [journee]);
      return toRegistration(eventId, entryOrThrow(eventId, passagerId));
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
      const bySignUp = (a: StoredEntry, b: StoredEntry) => a.signedUpAt.localeCompare(b.signedUpAt);
      return {
        group: { groupId: led.groupId, name: led.name },
        events: concerned
          .filter((event) => event && event.status !== "archive")
          .map((event) => {
            const entries = read(event!.eventId);
            return {
              eventId: event!.eventId,
              name: event!.name,
              startDateTime: event!.startDateTime,
              status: event!.status as EventStatus,
              maximum: maximumsOf(event!.eventId)[led.groupId] ?? null,
              members: [...entries].sort(bySignUp).filter(ridesWith(led.groupId)).map((e) => toEntry(all, event!.eventId, e)),
              demandes: [...entries].sort(bySignUp).filter(asksFor(led.groupId)).map((e) => toEntry(all, event!.eventId, e)),
              journees: eventJournees(event!.startDateTime, event!.endDateTime).map((journee) => ({
                journee,
                members: at(entries, journee).filter(ridesWith(led.groupId)).map((e) => toEntry(all, event!.eventId, e, journee)),
                demandes: at(entries, journee).filter(asksFor(led.groupId)).map((e) => toEntry(all, event!.eventId, e, journee)),
              })),
            };
          })
          .sort((a, b) => a.startDateTime.localeCompare(b.startDateTime)),
      };
    },

    fetchRoster,

    /** Faire monter de Journées (aucune : toutes celles où l'on attend) ; un binôme monte ensemble, prenant deux places. */
    promote: async (eventId: string, personId: string, journees?: Journee[]): Promise<Roster> => {
      const event = await eventOrThrow(eventId);
      await refuseOnArchived(eventId);
      const entry = entryOrThrow(eventId, personId);
      const waiting = entry.journees.filter((j) => j.waiting && (!journees?.length || journees.includes(j.journee)));
      if (waiting.length === 0) throw new Error("Cette personne n'est pas sur la liste d'attente.");
      waiting.forEach(({ journee }) => {
        const partner = conducteurOn(eventId, entryOrThrow(eventId, personId), journee) ?? passagerOf(eventId, personId, journee);
        const partnerWaits = partner ? journeeOf(partner, journee)!.waiting : false;
        if (!placeLeft(eventId, event.maxParticipants, journee, partnerWaits ? 2 : 1))
          throw new Error("L'événement a atteint son maximum de participants.");
        [personId, ...(partner ? [partner.personId] : [])].forEach((id) => {
          const e = entryOrThrow(eventId, id);
          update(eventId, id, { journees: e.journees.map((j) => (j.journee === journee ? { ...j, waiting: false } : j)) });
        });
      });
      return fetchRoster(eventId, journees?.[0]);
    },

    /** Retirer de Journées (aucune : toutes) ; `withPassager` : le passager est retiré aussi ces Journées-là. */
    remove: async (eventId: string, personId: string, journees?: Journee[], withPassager = true): Promise<Roster> => {
      await refuseOnChef(entryOrThrow(eventId, personId));
      await refuseOnArchived(eventId);
      leave(eventId, personId, journees, withPassager);
      return fetchRoster(eventId, journees?.[0]);
    },

    /** Comme EventServiceImpl.update : les inscriptions aux Journées qu'un Événement n'a plus sont retirées. */
    keepJourneesOf: async (eventId: string) => {
      const kept = await journeesOfEvent(eventId);
      write(
        eventId,
        read(eventId)
          .map((e) => ({ ...e, journees: e.journees.filter((j) => kept.includes(j.journee)) }))
          .filter((e) => e.journees.length > 0)
      );
    },
  };
}
