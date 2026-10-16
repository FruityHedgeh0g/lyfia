import { mockEvents } from "./eventFixtures";
import { Event } from "../../features/events/types";
import { allowedTransitions, currentStatus, EventStatus } from "../../features/events/status";
import { JsonStore } from "./jsonStore";
import { createOverlayCollection } from "./overlayCollection";

/**
 * Le comportement d'EventController, en mémoire, derrière le faux backend (test/fakeApi) : l'ancien client
 * mocké des Événements. Les règles elles-mêmes sont testées côté Java.
 */
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

export function createEventsApi(store: JsonStore, closedSectorIds: () => Set<string>) {
  const events = createOverlayCollection<Event>({ store, name: "event", fixtures: mockEvents, idOf: (e) => e.eventId });
  /** Le statut renvoyé est le statut courant, comme côté backend. */
  const withCurrentStatus = (event: Event): Event => ({ ...event, status: currentStatus(event) });

  const visible = async (seesPlanification: boolean, seesClosedSecteurs: boolean) => {
    const closed = seesClosedSecteurs ? new Set<string>() : closedSectorIds();
    return (event: Event) => (seesPlanification || event.status !== "planification") && !closed.has(event.sectorId);
  };
  const refuseInClosedSector = (sectorId: string | undefined) => {
    if (sectorId && closedSectorIds().has(sectorId)) throw new Error("Ce secteur est fermé : il est en lecture seule.");
  };
  const refuseWhenClosed = async (eventId: string) => refuseInClosedSector((await events.get(eventId))?.sectorId);

  return {
    fetchEvents: async (seesPlanification: boolean, seesClosedSecteurs = false) => {
      const isVisible = await visible(seesPlanification, seesClosedSecteurs);
      return (await events.list()).filter(isVisible).map(withCurrentStatus);
    },
    fetchEventById: async (eventId: string, seesPlanification: boolean, seesClosedSecteurs = false) => {
      const event = await events.get(eventId);
      return event && (await visible(seesPlanification, seesClosedSecteurs))(event) ? withCurrentStatus(event) : undefined;
    },
    updateEvent: async (eventId: string, patch: Omit<EventInput, "sectorId">) => {
      await refuseWhenClosed(eventId);
      await events.update(eventId, { ...patch, maxParticipants: patch.maxParticipants ? patch.maxParticipants : null });
    },
    /** À la fermeture d'un Secteur : Archivé s'il a eu (ou a) lieu, Annulé sinon ; les inscriptions restent. */
    closeEventsOfSector: async (sectorId: string) => {
      for (const event of (await events.list()).filter((e) => e.sectorId === sectorId)) {
        const current = currentStatus(event);
        await events.update(event.eventId, { status: current === "en_cours" || current === "archive" ? "archive" : "annule" });
      }
    },
    createEvent: async (input: EventInput): Promise<Event> => {
      refuseInClosedSector(input.sectorId);
      const event: Event = { eventId: `event-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, status: "planification", ...input };
      await events.create(event);
      return event;
    },
    changeStatus: async (eventId: string, status: EventStatus) => {
      await refuseWhenClosed(eventId);
      const event = await events.get(eventId);
      if (!event || !allowedTransitions(currentStatus(event)).includes(status)) {
        throw new Error("Transition de statut refusée");
      }
      await events.update(eventId, { status });
      return withCurrentStatus({ ...event, status });
    },
  };
}
