import React from "react";
import { useEvents } from "../../../features/events/useEvents";
import { EVENT_STATUS_LABELS, statusTone } from "../../../features/events/status";
import { isUpcoming } from "../../../features/events/eventsApi";
import { Event } from "../../../features/events/types";
import MediaCard from "../../molecules/MediaCard/MediaCard";
import Badge from "../../atoms/Badge/Badge";
import Spinner from "../../atoms/Spinner/Spinner";
import { placeholderImage } from "../../../lib/placeholderImage";
import { formatDateRange } from "../../../lib/formatDate";
import { inSecteurChoice, useSecteurChoice } from "../../../features/sectors/secteurChoice";
import styles from "./EventList.module.css";

export interface EventListProps {
  scope?: "all" | "upcoming";
  limit?: number;
  emptyMessage?: string;
}

function renderCard(event: Event, options: { headingLevel?: "h2" | "h3"; dimmed?: boolean } = {}) {
  return (
    <MediaCard
      key={event.eventId}
      to={`/evenements/${event.eventId}`}
      imageSrc={event.imageUrl || placeholderImage(event.eventId, event.name)}
      imageAlt={event.name}
      title={event.name}
      subtitle={formatDateRange(event.startDateTime, event.endDateTime)}
      excerpt={event.description}
      badge={<Badge label={EVENT_STATUS_LABELS[event.status]} tone={statusTone(event.status)} />}
      headingLevel={options.headingLevel}
      dimmed={options.dimmed}
    />
  );
}

export const EventList: React.FC<EventListProps> = ({
  scope = "all",
  limit,
  emptyMessage = "Aucun événement planifié pour le moment. Revenez bientôt !",
}) => {
  const { data: events, isLoading, isError } = useEvents();
  const { sectorId: choice } = useSecteurChoice();

  if (isLoading) return <Spinner label="Chargement des événements..." />;
  if (isError) return <p className={styles.error}>Impossible de charger les événements pour le moment.</p>;

  const all = (events ?? []).filter((e) => inSecteurChoice(choice, e.sectorId));

  if (scope === "upcoming") {
    let upcoming = all.filter((e) => isUpcoming(e)).sort((a, b) => a.startDateTime.localeCompare(b.startDateTime));
    if (limit) upcoming = upcoming.slice(0, limit);
    if (upcoming.length === 0) return <p className={styles.empty}>{emptyMessage}</p>;
    return <div className={styles.grid}>{upcoming.map((e) => renderCard(e))}</div>;
  }

  // scope === "all" : les événements passés sont regroupés à part et grisés,
  // pour signaler qu'ils ne sont plus disponibles.
  const upcoming = all.filter((e) => isUpcoming(e)).sort((a, b) => a.startDateTime.localeCompare(b.startDateTime));
  const past = all.filter((e) => !isUpcoming(e)).sort((a, b) => b.startDateTime.localeCompare(a.startDateTime));

  if (upcoming.length === 0 && past.length === 0) return <p className={styles.empty}>{emptyMessage}</p>;

  return (
    <div className={styles.groups}>
      {upcoming.length > 0 && (
        <div>
          <h2 className={styles.groupTitle}>À venir</h2>
          <div className={styles.grid}>{upcoming.map((e) => renderCard(e, { headingLevel: "h3" }))}</div>
        </div>
      )}
      {past.length > 0 && (
        <div>
          <h2 className={styles.groupTitle}>Événements passés</h2>
          <div className={styles.grid}>{past.map((e) => renderCard(e, { headingLevel: "h3", dimmed: true }))}</div>
        </div>
      )}
    </div>
  );
};

export default EventList;
