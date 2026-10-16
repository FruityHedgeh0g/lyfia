import { EventStatus } from "./status";

/** Reflète EventDto côté backend (vue Detailed). */
export interface Event {
  eventId: string;
  /** Statut courant (calculé par l'API à partir du statut enregistré et des dates). */
  status: EventStatus;
  name: string;
  description: string;
  startDateTime: string; // ISO 8601
  endDateTime: string; // ISO 8601
  /** Secteur auquel appartient l'Événement. */
  sectorId: string;
  /** Maximum global de Participants ; au-delà, les inscriptions vont en liste d'attente. */
  maxParticipants?: number | null;
  /** Le Media de son image (choisi avec le sélecteur d'image) ; aucun : l'image par défaut. */
  imageMediaId?: string | null;
  address?: string;
  addressComplement?: string;
  city?: string;
  postalCode?: string;
  country?: string;
}

/** L'image d'un Event : celle de son Media, sinon `fallback` (l'image par défaut de la page). */
export const eventImageSrc = <T extends string | undefined>(event: Pick<Event, "imageMediaId">, fallback: T) =>
  event.imageMediaId ? `/api/medias/${encodeURIComponent(event.imageMediaId)}/content?variante=affichage` : fallback;
