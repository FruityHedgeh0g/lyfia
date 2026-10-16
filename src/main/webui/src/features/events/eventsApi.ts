import { Event } from "./types";
import { EventStatus } from "./status";
import { apiFetch, HttpError } from "../../lib/http";

export interface EventInput {
  name: string;
  description: string;
  startDateTime: string;
  endDateTime: string;
  sectorId: string;
  /** 0 retire le maximum, comme côté backend. */
  maxParticipants?: number;
  /** Le Media de son image ; null : l'image par défaut. */
  imageMediaId?: string | null;
  address?: string;
  city?: string;
  postalCode?: string;
  country?: string;
}

/** EventDto : le Secteur y est aussi imbriqué ; le statut est le statut courant, calculé par l'API. */
type EventDto = Event & { sector?: { sectorId: string; name: string } | null };

const toEvent = ({ sector, ...dto }: EventDto): Event => ({
  ...dto,
  sectorId: dto.sectorId ?? sector?.sectorId,
  description: dto.description ?? "",
});

const base = (eventId: string) => `/api/events/${encodeURIComponent(eventId)}`;

/**
 * Les Événements, sur EventController. L'API ne montre la Planification qu'au Bureau, et les Événements
 * d'un Secteur fermé qu'au Super admin ; ils ne changent plus. Pas de suppression : on annule. Lire les
 * Événements ne demande pas d'être connecté.
 */
export async function fetchEvents(): Promise<Event[]> {
  return (await apiFetch<EventDto[]>("/api/events")).map(toEvent);
}

/** undefined pour un Événement inconnu ou que la personne ne voit pas. */
export async function fetchEventById(eventId: string): Promise<Event | undefined> {
  try {
    return toEvent(await apiFetch<EventDto>(base(eventId)));
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}

export async function updateEvent(eventId: string, patch: Omit<EventInput, "sectorId">): Promise<Event> {
  return toEvent(await apiFetch<EventDto>("/api/events", { method: "PATCH", body: JSON.stringify({ eventId, ...patch, maxParticipants: patch.maxParticipants ?? 0 }) }));
}

/** Démarre en Planification ; le Bureau crée dans son propre Secteur, seul le Super admin le choisit. */
export async function createEvent(input: EventInput): Promise<Event> {
  return toEvent(await apiFetch<EventDto>("/api/events", { method: "POST", body: JSON.stringify(input) }));
}

export async function changeStatus(eventId: string, status: EventStatus): Promise<Event> {
  return toEvent(await apiFetch<EventDto>(`${base(eventId)}/status`, { method: "PUT", body: JSON.stringify({ status }) }));
}

export function isUpcoming(event: Event, now: Date = new Date()): boolean {
  return new Date(event.endDateTime) >= now;
}
