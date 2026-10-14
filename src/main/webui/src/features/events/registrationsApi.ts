import { apiFetch, HttpError } from "../../lib/http";
import { EventStatus } from "./status";
import { GroupKind } from "../groups/types";
import { Journee } from "../../lib/journees";

/** Où en est une inscription : place confirmée (Participant) ou Liste d'attente. */
export type RegistrationStatus = "participant" | "en_attente";

/** Où en est une Demande de groupe. */
export type DemandeStatus = "en_attente" | "acceptee" | "refusee";

/** Ce que la personne fait à l'Événement, choisi pour chaque Événement ; chaque Poste compte dans les maximums. */
export type Poste = "conducteur" | "passager" | "chauffeur_camionnette" | "point_fixe" | "service";

export const POSTE_LABELS: Record<Poste, string> = {
  conducteur: "Conducteur",
  passager: "Passager",
  chauffeur_camionnette: "Chauffeur camionnette",
  point_fixe: "Point fixe",
  service: "Service",
};

export const POSTES = Object.keys(POSTE_LABELS) as Poste[];

/** La sorte de Groupe avec laquelle on est à ce Poste ; aucune pour un Point fixe. */
export const groupKindOf = (poste: Poste): GroupKind | null =>
  poste === "service" ? "service" : poste === "point_fixe" ? null : "motards";

/** Reflète NestedUserDto : une personne nommée, sans ses coordonnées. */
export interface PersonRef {
  userId: string;
  firstName: string;
  lastName: string;
}

/** Reflète GroupRefDto côté backend. */
export interface GroupRef {
  groupId: string;
  name: string;
}

/** Reflète DemandeDto : le Groupe demandé et l'état de la Demande. */
export interface Demande {
  group: GroupRef;
  status: DemandeStatus;
}

/** Reflète RegistrationDto.JourneeDto : une Journée à laquelle on est inscrit, et qui roule avec soi ce jour-là. */
export interface RegistrationJournee {
  journee: Journee;
  status: RegistrationStatus;
  /** Le conducteur derrière lequel roule un passager ce jour-là. */
  conducteur: PersonRef | null;
  /** Le passager qui roule derrière un conducteur ce jour-là. */
  passager: PersonRef | null;
}

/**
 * Reflète RegistrationDto côté backend : sa propre inscription à un Événement, son Poste, son Groupe et sa Demande pour
 * tout l'Événement, et chaque Journée à laquelle on est inscrit (ADR 0011). `status` : en attente seulement si on
 * l'est chaque Journée.
 */
export interface Registration {
  eventId: string;
  poste: Poste;
  status: RegistrationStatus;
  signedUpAt: string;
  /** Groupe avec lequel on roule à cet Événement, une fois accepté ou placé. */
  group: GroupRef | null;
  demande: Demande | null;
  /** Le conducteur de la première Journée où un passager en a un. */
  conducteur: PersonRef | null;
  /** On y roule comme Chef du Groupe qu'on mène : une place que seul un changement d'Affectation retire (ADR 0010). */
  leadsGroup?: boolean;
  journees: RegistrationJournee[];
}

/**
 * Reflète RosterEntryDto côté backend : une personne inscrite, vue par le Bureau ou son Chef de groupe, pour une
 * Journée — sa place, son conducteur ou son passager ce jour-là — avec toutes ses Journées.
 */
export interface RosterEntry {
  personId: string;
  firstName: string;
  lastName: string;
  phone: string;
  poste: Poste;
  signedUpAt: string;
  group: GroupRef | null;
  demande: Demande | null;
  /** Le conducteur derrière lequel roule un passager ce jour-là ; un passager sans conducteur attend d'être associé. */
  conducteur: PersonRef | null;
  /** Le passager qui roule derrière un conducteur ce jour-là. */
  passager: PersonRef | null;
  /** Le Chef du Groupe : ni retiré, ni sorti du groupe, tant qu'il le mène (ADR 0010). */
  leadsGroup?: boolean;
  journees: Journee[];
}

/** Un passager sans conducteur ce jour-là, signalé au Bureau et à son Chef de groupe pour être associé. */
export const withoutConducteur = (entry: Pick<RosterEntry, "poste" | "conducteur">) => entry.poste === "passager" && !entry.conducteur;

/**
 * Reflète GroupRosterDto : un Groupe à un Événement, son maximum (null : sans
 * limite), qui y roule, et ses Demandes en attente dans l'ordre d'inscription —
 * la Liste d'attente du Groupe une fois le maximum atteint.
 */
export interface GroupRoster {
  group: GroupRef;
  maximum: number | null;
  members: RosterEntry[];
  demandes: RosterEntry[];
}

/** Combien de personnes sont inscrites à une Journée : confirmées et sur sa Liste d'attente. */
export interface JourneeCount {
  journee: Journee;
  participants: number;
  waiting: number;
}

/**
 * Reflète RosterDto : pour une Journée, Participants et Liste d'attente, chacun dans l'ordre d'inscription, et chaque
 * Groupe du Secteur ; `journees` compte chaque Journée de l'Événement.
 */
export interface Roster {
  eventId: string;
  journee: Journee;
  journees: JourneeCount[];
  maxParticipants: number | null;
  participants: RosterEntry[];
  waiting: RosterEntry[];
  groups: GroupRoster[];
}

/** Reflète MonGroupeDto : le Groupe mené (null sans Affectation) et, par Événement, ses membres et Demandes en attente. */
export interface MonGroupe {
  group: GroupRef | null;
  events: {
    eventId: string;
    name: string;
    startDateTime: string;
    status: EventStatus;
    /** Maximum du Groupe à cet Événement, null : sans limite. */
    maximum: number | null;
    /** Tous ceux avec le Groupe, ou qui le demandent, quelle que soit la Journée. */
    members: RosterEntry[];
    demandes: RosterEntry[];
    /** Les mêmes, Journée par Journée, avec chaque binôme ce jour-là. */
    journees: { journee: Journee; members: RosterEntry[]; demandes: RosterEntry[] }[];
  }[];
}



/** Qui agit : un Chef n'agit que sur le Groupe qu'il mène (Affectation), le Bureau sur tous. */
export interface Actor {
  personId: string;
  bureau: boolean;
}

/** Personne qui s'inscrit (le backend lit ces informations dans son profil). */
export interface SigningUpPerson {
  userId: string;
  firstName: string;
  lastName: string;
  phone?: string;
  /** Secteur de la personne à partir de Membre : elle ne roule qu'aux Événements de ce Secteur (ADR 0004). */
  sectorId?: string | null;
}

export const REGISTRATION_STATUS_LABELS: Record<RegistrationStatus, string> = {
  participant: "Participant",
  en_attente: "En attente",
};

/**
 * Le tableur des Participants d'un Événement (GET /api/events/{eventId}/roster/export, Bureau) : une feuille par
 * Journée, la liste du jour sur le terrain. Un fichier ne se mocke
 * pas : le lien vise directement l'API, la session suffit à s'authentifier.
 */
export const rosterExportUrl = (eventId: string) => `/api/events/${encodeURIComponent(eventId)}/roster/export`;

/** « Passager de Prénom Nom », pour qui roule avec un conducteur. */
export const passagerLabel = (conducteur: PersonRef) => `Passager de ${conducteur.firstName} ${conducteur.lastName}`;

export const DEMANDE_STATUS_LABELS: Record<DemandeStatus, string> = {
  en_attente: "en attente",
  acceptee: "acceptée",
  refusee: "refusée",
};

/** Réponse 422 `phone-required` de l'API : il faut un téléphone pour s'inscrire. */
export class PhoneRequiredError extends Error {
  constructor() {
    super("Un numéro de téléphone est nécessaire pour s'inscrire.");
    this.name = "PhoneRequiredError";
  }
}

/**
 * Réponse 422 `conducteur-introuvable` ou `conducteur-ambigu` : le nom tapé ne désigne clairement aucun conducteur
 * inscrit, libre l'une des journées choisies. La liste des inscrits n'est jamais montrée.
 */
export class ConducteurNotFoundError extends Error {
  constructor(readonly ambiguous: boolean) {
    super(
      ambiguous
        ? "Plusieurs conducteurs inscrits ressemblent à ce nom : précisez son prénom et son nom."
        : "Aucun conducteur inscrit ces journées-là ne correspond à ce nom. Vérifiez l'orthographe, ou inscrivez-vous sans conducteur pour l'instant."
    );
    this.name = "ConducteurNotFoundError";
  }
}

const base = (eventId: string) => `/api/events/${encodeURIComponent(eventId)}`;
const id = encodeURIComponent;

/** Sans téléphone au profil, l'API répond 422 `phone-required` ; sans conducteur reconnu, `conducteur-…`. */
async function phoneRequired<T>(call: Promise<T>): Promise<T> {
  try {
    return await call;
  } catch (error) {
    if (error instanceof HttpError && error.status === 422) {
      if (error.code === "conducteur-introuvable" || error.code === "conducteur-ambigu")
        throw new ConducteurNotFoundError(error.code === "conducteur-ambigu");
      throw new PhoneRequiredError();
    }
    throw error;
  }
}

/**
 * Inscriptions, listes et Demandes de groupe, sur EventController. L'API sait qui est connecté : ses
 * inscriptions, son Groupe s'il est Chef, et ce qu'il peut décider (le Chef sur le Groupe qu'il mène,
 * le Bureau sur tous). Une seule liste par Événement alimente toutes ces vues.
 */
export const fetchMyRegistrations = () => apiFetch<Registration[]>("/api/events/registrations");

const withJournees = (params: URLSearchParams, journees?: Journee[]) => {
  (journees ?? []).forEach((j) => params.append("journee", j));
  return params;
};

const query = (params: URLSearchParams) => (params.size ? `?${params}` : "");

/**
 * S'inscrire aux Journées choisies (aucune : tout l'Événement), à un Poste ; `groupId` demande ce Groupe ;
 * `conducteur` : le prénom et le nom du conducteur avec qui vient un passager (« je viens avec »), rapproché par l'API
 * parmi les inscrits. S'inscrire de nouveau ajoute des Journées.
 */
export function signUp(
  eventId: string,
  options: { poste?: Poste; groupId?: string; conducteur?: string; journees?: Journee[] } = {}
): Promise<Registration> {
  const params = withJournees(new URLSearchParams(), options.journees);
  if (options.poste) params.set("poste", options.poste);
  if (options.conducteur) params.set("conducteur", options.conducteur);
  else if (options.groupId) params.set("groupId", options.groupId);
  const query = params.size ? `?${params}` : "";
  return phoneRequired(apiFetch<Registration>(`${base(eventId)}/registration${query}`, { method: "PUT" }));
}

/** Se retirer de Journées (aucune : toutes) ; `withPassager` : son passager se retire aussi ces Journées-là. */
export const withdraw = (eventId: string, options: { journees?: Journee[]; withPassager?: boolean } = {}) => {
  const params = withJournees(new URLSearchParams(), options.journees);
  params.set("withPassager", String(options.withPassager ?? true));
  return apiFetch<void>(`${base(eventId)}/registration${query(params)}`, { method: "DELETE" });
};

export const requestGroup = (eventId: string, groupId: string) =>
  apiFetch<Registration>(`${base(eventId)}/registration/demande`, { method: "PUT", body: JSON.stringify({ groupId }) });

export const decideDemande = (eventId: string, personId: string, accept: boolean) =>
  apiFetch<Registration>(`${base(eventId)}/demandes/${id(personId)}/${accept ? "accept" : "refuse"}`, { method: "POST" });

/** `withPassager` : les passagers d'un conducteur le suivent, sinon leur binôme prend fin. */
export const placeInGroup = (eventId: string, personId: string, groupId: string, withPassager = true) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/group/${id(groupId)}?withPassager=${withPassager}`, { method: "PUT" });

/** Le Bureau change le Poste de quelqu'un : jamais en passager, ni pour le Chef qui mène son Groupe. */
export const changePoste = (eventId: string, personId: string, poste: Poste) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/poste`, { method: "PUT", body: JSON.stringify({ poste }) });

/** null ou 0 retire le maximum. */
export const setGroupMaximum = (eventId: string, groupId: string, maximum: number | null) =>
  apiFetch<Roster>(`${base(eventId)}/groups/${id(groupId)}/maximum`, { method: "PUT", body: JSON.stringify({ maximum }) });

/** `withPassager` : les passagers d'un conducteur sortent aussi du Groupe, sinon leur binôme prend fin. */
export const takeOutOfGroup = (eventId: string, personId: string, withPassager = true) =>
  apiFetch<Registration>(`${base(eventId)}/roster/${id(personId)}/group?withPassager=${withPassager}`, { method: "DELETE" });

/** Le Bureau ou le Chef du passager l'associe à un conducteur de son Groupe pour une Journée. */
export const pair = (eventId: string, passagerId: string, journee: Journee, conducteurId: string) =>
  apiFetch<Registration>(`${base(eventId)}/roster/${id(passagerId)}/journees/${journee}/conducteur/${id(conducteurId)}`, { method: "PUT" });

/** Le Bureau ou le Chef du passager met fin à son binôme ce jour-là. */
export const unpair = (eventId: string, passagerId: string, journee: Journee) =>
  apiFetch<Registration>(`${base(eventId)}/roster/${id(passagerId)}/journees/${journee}/conducteur`, { method: "DELETE" });

export const fetchMonGroupe = () => apiFetch<MonGroupe>("/api/events/mon-groupe");

/** La liste d'une Journée (aucune : la première de l'Événement). */
export const fetchRoster = (eventId: string, journee?: Journee) =>
  apiFetch<Roster>(`${base(eventId)}/roster${journee ? `?journee=${journee}` : ""}`);

/** Faire monter de la Liste d'attente de Journées (aucune : toutes celles où l'on attend) ; un binôme monte ensemble. */
export const promote = (eventId: string, personId: string, journees?: Journee[]) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/promote${query(withJournees(new URLSearchParams(), journees))}`, { method: "POST" });

/** Retirer de Journées (aucune : toutes) ; `withPassager` : le passager est retiré aussi ces Journées-là. */
export const remove = (eventId: string, personId: string, options: { journees?: Journee[]; withPassager?: boolean } = {}) => {
  const params = withJournees(new URLSearchParams(), options.journees);
  params.set("withPassager", String(options.withPassager ?? true));
  return apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}${query(params)}`, { method: "DELETE" });
};
