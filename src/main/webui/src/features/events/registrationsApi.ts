import { apiFetch, HttpError } from "../../lib/http";
import { EventStatus } from "./status";
import { GroupKind } from "../groups/types";

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

/** Reflète RegistrationDto côté backend : sa propre inscription à un Événement. */
export interface Registration {
  eventId: string;
  poste: Poste;
  status: RegistrationStatus;
  signedUpAt: string;
  /** Groupe avec lequel on roule à cet Événement, une fois accepté ou placé. */
  group: GroupRef | null;
  demande: Demande | null;
  /** Le conducteur d'un passager ; null pour un conducteur. */
  conducteur: PersonRef | null;
  /** On y roule comme Chef du Groupe qu'on mène : une place que seul un changement d'Affectation retire (ADR 0010). */
  leadsGroup?: boolean;
}

/** Reflète RosterEntryDto côté backend : une personne inscrite, vue par le Bureau ou son Chef de groupe. */
export interface RosterEntry {
  personId: string;
  firstName: string;
  lastName: string;
  phone: string;
  poste: Poste;
  signedUpAt: string;
  group: GroupRef | null;
  demande: Demande | null;
  conducteur: PersonRef | null;
  /** Nombre de passagers d'un conducteur : ils comptent avec lui dans chaque maximum. */
  passagers: number;
  /** Le Chef du Groupe : ni retiré, ni sorti du groupe, tant qu'il le mène (ADR 0010). */
  leadsGroup?: boolean;
}

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

/** Reflète RosterDto : Participants et Liste d'attente, chacun dans l'ordre d'inscription, et chaque Groupe du Secteur. */
export interface Roster {
  eventId: string;
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
    members: RosterEntry[];
    demandes: RosterEntry[];
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
 * Le tableur des Participants d'un Événement (GET /api/events/{eventId}/roster/export, Bureau) : un onglet par
 * Groupe, un pour les Participants sans groupe, un pour la Liste d'attente. Un fichier ne se mocke
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

const base = (eventId: string) => `/api/events/${encodeURIComponent(eventId)}`;
const id = encodeURIComponent;

/** Sans téléphone au profil, l'API répond 422 `phone-required`. */
async function phoneRequired<T>(call: Promise<T>): Promise<T> {
  try {
    return await call;
  } catch (error) {
    if (error instanceof HttpError && error.status === 422) throw new PhoneRequiredError();
    throw error;
  }
}

/**
 * Inscriptions, listes et Demandes de groupe, sur EventController. L'API sait qui est connecté : ses
 * inscriptions, son Groupe s'il est Chef, et ce qu'il peut décider (le Chef sur le Groupe qu'il mène,
 * le Bureau sur tous). Une seule liste par Événement alimente toutes ces vues.
 */
export const fetchMyRegistrations = () => apiFetch<Registration[]>("/api/events/registrations");

/** À un Poste autre que passager, `groupId` demande ce Groupe ; `conducteurId` inscrit comme passager de ce conducteur. */
export function signUp(eventId: string, options: { poste?: Poste; groupId?: string; conducteurId?: string } = {}): Promise<Registration> {
  const params = new URLSearchParams();
  if (options.poste) params.set("poste", options.poste);
  if (options.conducteurId) params.set("conducteurId", options.conducteurId);
  else if (options.groupId) params.set("groupId", options.groupId);
  const query = params.size ? `?${params}` : "";
  return phoneRequired(apiFetch<Registration>(`${base(eventId)}/registration${query}`, { method: "PUT" }));
}

export const withdraw = (eventId: string) => apiFetch<void>(`${base(eventId)}/registration`, { method: "DELETE" });

export const fetchConducteurs = (eventId: string) => apiFetch<PersonRef[]>(`${base(eventId)}/conducteurs`);

export const requestGroup = (eventId: string, groupId: string) =>
  apiFetch<Registration>(`${base(eventId)}/registration/demande`, { method: "PUT", body: JSON.stringify({ groupId }) });

export const decideDemande = (eventId: string, personId: string, accept: boolean) =>
  apiFetch<Registration>(`${base(eventId)}/demandes/${id(personId)}/${accept ? "accept" : "refuse"}`, { method: "POST" });

export const placeInGroup = (eventId: string, personId: string, groupId: string) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/group/${id(groupId)}`, { method: "PUT" });

/** Le Bureau change le Poste de quelqu'un : jamais en passager, ni pour le Chef qui mène son Groupe. */
export const changePoste = (eventId: string, personId: string, poste: Poste) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/poste`, { method: "PUT", body: JSON.stringify({ poste }) });

/** null ou 0 retire le maximum. */
export const setGroupMaximum = (eventId: string, groupId: string, maximum: number | null) =>
  apiFetch<Roster>(`${base(eventId)}/groups/${id(groupId)}/maximum`, { method: "PUT", body: JSON.stringify({ maximum }) });

export const takeOutOfGroup = (eventId: string, personId: string) =>
  apiFetch<Registration>(`${base(eventId)}/roster/${id(personId)}/group`, { method: "DELETE" });

export const fetchMonGroupe = () => apiFetch<MonGroupe>("/api/events/mon-groupe");

export const fetchRoster = (eventId: string) => apiFetch<Roster>(`${base(eventId)}/roster`);

export const promote = (eventId: string, personId: string) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}/promote`, { method: "POST" });

export const remove = (eventId: string, personId: string) =>
  apiFetch<Roster>(`${base(eventId)}/roster/${id(personId)}`, { method: "DELETE" });
