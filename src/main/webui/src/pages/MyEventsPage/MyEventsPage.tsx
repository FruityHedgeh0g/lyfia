import React from "react";
import { useEvents } from "../../features/events/useEvents";
import { useMyRegistrations } from "../../features/events/useMyRegistrations";
import { DEMANDE_STATUS_LABELS, passagerLabel, POSTE_LABELS, Registration, REGISTRATION_STATUS_LABELS } from "../../features/events/registrationsApi";
import { EVENT_STATUS_LABELS } from "../../features/events/status";
import { formatDateRange } from "../../lib/formatDate";
import MediaCard from "../../components/molecules/MediaCard/MediaCard";
import Badge from "../../components/atoms/Badge/Badge";
import Spinner from "../../components/atoms/Spinner/Spinner";
import Icon from "../../components/atoms/Icon/Icon";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import { placeholderImage } from "../../lib/placeholderImage";
import { eventJournees } from "../../lib/journees";
import styles from "./MyEventsPage.module.css";

/** Son Poste, et où l'on en est côté Groupe : le Groupe rejoint, ou la dernière Demande et son état. */
const groupLine = (registration: Registration) => {
  const poste = registration.conducteur ? passagerLabel(registration.conducteur) : POSTE_LABELS[registration.poste];
  if (registration.group) return `${poste} · ${registration.leadsGroup ? "Chef de groupe" : "Groupe"} : ${registration.group.name}`;
  if (registration.demande && !registration.conducteur)
    return `${poste} · Demande pour ${registration.demande.group.name} : ${DEMANDE_STATUS_LABELS[registration.demande.status]}`;
  return poste;
};

/** À quelles Journées on vient, quand ce n'est pas tout l'Événement (ADR 0011). */
const journeesLine = (registration: Registration, total: number) =>
  registration.journees.length < total ? `${registration.journees.length} journée(s) sur ${total}` : undefined;

export const MyEventsPage: React.FC = () => {
  const { data: events, isLoading: eventsLoading } = useEvents();
  const { data: registrations, isLoading: registrationsLoading } = useMyRegistrations();

  if (eventsLoading || registrationsLoading) return <Spinner label="Chargement de vos événements..." />;

  const myEvents = (events ?? []).flatMap((event) => {
    const registration = registrations?.find((r) => r.eventId === event.eventId);
    return registration ? [{ event, registration }] : [];
  });

  if (myEvents.length === 0) {
    return (
      <div className={styles.empty}>
        <span className={styles.emptyIcon} aria-hidden="true">
          <Icon name="calendar" size={30} />
        </span>
        <p>Vous n'êtes inscrit à aucun événement pour le moment.</p>
        <ButtonLink to="/evenements" label="Découvrir les événements" variant="accent" arrow />
      </div>
    );
  }

  return (
    <div className={styles.grid}>
      {myEvents.map(({ event, registration }) => (
        <MediaCard
          key={event.eventId}
          to={`/evenements/${event.eventId}`}
          imageSrc={event.imageUrl || placeholderImage(event.eventId, event.name)}
          imageAlt={event.name}
          title={event.name}
          subtitle={[
            EVENT_STATUS_LABELS[event.status],
            formatDateRange(event.startDateTime, event.endDateTime),
            journeesLine(registration, eventJournees(event.startDateTime, event.endDateTime).length),
            groupLine(registration),
          ]
            .filter(Boolean)
            .join(" · ")}
          badge={
            <Badge
              label={REGISTRATION_STATUS_LABELS[registration.status]}
              tone={registration.status === "participant" ? "accent" : "muted"}
            />
          }
        />
      ))}
    </div>
  );
};

export default MyEventsPage;
