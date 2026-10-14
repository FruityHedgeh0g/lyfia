import React from "react";
import { useEventMutations, useEvents } from "../../features/events/useEvents";
import { EventInput } from "../../features/events/eventsApi";
import { allowedTransitions, EVENT_STATUS_LABELS, EventStatus } from "../../features/events/status";
import { Event } from "../../features/events/types";
import { useIsSuperAdmin, useSectors } from "../../features/sectors/useSector";
import { useAuth } from "../../auth/AuthContext";
import { formatDateRange } from "../../lib/formatDate";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { useGroupMaximum } from "../../features/events/useRoster";
import EventRoster from "./EventRoster";

/** Le statut n'est modifiable qu'à l'édition, vers les seules transitions permises. */
/** `maxParticipants` vide : pas de maximum. `groupMaxima` : maximums des Groupes touchés, par groupId (vide : sans limite). */
type EventDraft = Omit<EventInput, "maxParticipants"> & { status: EventStatus; maxParticipants: string; groupMaxima: Record<string, string> };

const toDraft = (event: Event): EventDraft => ({
  name: event.name,
  description: event.description,
  startDateTime: event.startDateTime,
  endDateTime: event.endDateTime,
  sectorId: event.sectorId,
  status: event.status,
  maxParticipants: event.maxParticipants ? String(event.maxParticipants) : "",
  imageUrl: event.imageUrl ?? "",
  address: event.address ?? "",
  city: event.city ?? "",
  postalCode: event.postalCode ?? "",
  country: event.country ?? "France",
  groupMaxima: {},
});

/** Le Bureau et les Admins gèrent les Événements de leur Secteur ; le Super admin ceux de tous (ADR 0004). */
export const EventsAdminPage: React.FC = () => {
  const isSuperAdmin = useIsSuperAdmin();
  const ownSectorId = useAuth().user?.sector?.sectorId ?? "";
  const { data: allEvents, isLoading } = useEvents();
  const events = isSuperAdmin ? allEvents : allEvents?.filter((e) => e.sectorId === ownSectorId);
  const { data: sectors } = useSectors();
  const { update, create, moveTo } = useEventMutations();
  const groupMaximum = useGroupMaximum();
  const { confirm, dialog } = useConfirm();

  const sectorOptions = (sectors ?? []).map((s) => ({ value: s.sectorId, label: s.name }));
  const emptyDraft: EventDraft = {
    name: "",
    description: "",
    startDateTime: "",
    endDateTime: "",
    sectorId: isSuperAdmin ? sectorOptions[0]?.value ?? "" : ownSectorId,
    status: "planification",
    maxParticipants: "",
    imageUrl: "",
    address: "",
    city: "",
    postalCode: "",
    country: "France",
    groupMaxima: {},
  };

  const save = async (eventId: string, { status, sectorId: _sectorId, maxParticipants, groupMaxima, ...patch }: EventDraft) => {
    const current = events?.find((e) => e.eventId === eventId)?.status;
    // Annulé est sans retour : rien n'est enregistré sans confirmation, et la fiche reste ouverte
    if (status === "annule" && status !== current) {
      const confirmed = await confirm({
        title: `Annuler « ${patch.name} » ?`,
        message: "Un événement annulé ne peut plus être rouvert. Les inscriptions restent enregistrées telles quelles.",
        confirmLabel: "Annuler l'événement",
        cancelLabel: "Garder l'événement",
      });
      if (!confirmed) throw new Error("Annulation abandonnée");
    }
    await update.mutateAsync({ eventId, ...patch, maxParticipants: Number(maxParticipants) || 0 });
    // Avant le statut : un Événement archivé n'accepte plus de maximum
    await Promise.all(
      Object.entries(groupMaxima).map(([groupId, maximum]) => groupMaximum.mutateAsync({ eventId, groupId, maximum: Number(maximum) || null }))
    );
    if (status !== current) await moveTo.mutateAsync({ eventId, status });
  };

  if (isLoading) return <Spinner label="Chargement des événements..." />;

  return (
    <>
      {dialog}
      <AdminCrudList
      title="Gestion des événements"
      hint="Un événement n'est jamais supprimé : annulez-le. En cours et Archivé suivent les dates."
      items={events ?? []}
      idOf={(e) => e.eventId}
      display={(e) => ({
        title: e.name,
        subtitle: [
          EVENT_STATUS_LABELS[e.status],
          formatDateRange(e.startDateTime, e.endDateTime),
          e.maxParticipants ? `${e.maxParticipants} places` : undefined,
        ]
          .filter(Boolean)
          .join(" · "),
      })}
      toDraft={toDraft}
      renderFields={(value, onChange, event) => {
        const transitions = event ? allowedTransitions(event.status) : [];
        return (
          <>
            <FormField label="Nom" value={value.name} onChange={(e) => onChange({ ...value, name: e.target.value })} required />
            <FormField
              label="Description"
              multiline
              rows={3}
              value={value.description}
              onChange={(e) => onChange({ ...value, description: e.target.value })}
            />
            <FormField
              label="Début"
              type="datetime-local"
              value={value.startDateTime}
              onChange={(e) => onChange({ ...value, startDateTime: e.target.value })}
              required
            />
            <FormField
              label="Fin"
              type="datetime-local"
              value={value.endDateTime}
              onChange={(e) => onChange({ ...value, endDateTime: e.target.value })}
              required
            />
            <FormField
              label="Participants maximum"
              type="number"
              min={1}
              value={value.maxParticipants}
              onChange={(e) => onChange({ ...value, maxParticipants: e.target.value })}
            />
            {!event && (
              <Select
                label="Secteur"
                value={value.sectorId}
                onChange={(sectorId) => onChange({ ...value, sectorId })}
                options={sectorOptions}
                disabled={!isSuperAdmin}
              />
            )}
            {event && transitions.length > 0 && (
              <Select
                label="Statut"
                value={value.status}
                onChange={(status) => onChange({ ...value, status: status as EventStatus })}
                options={[event.status, ...transitions].map((s) => ({ value: s, label: EVENT_STATUS_LABELS[s] }))}
              />
            )}
            <FormField label="Image (URL)" value={value.imageUrl} onChange={(e) => onChange({ ...value, imageUrl: e.target.value })} />
            <FormField label="Adresse" value={value.address} onChange={(e) => onChange({ ...value, address: e.target.value })} />
            <FormField label="Ville" value={value.city} onChange={(e) => onChange({ ...value, city: e.target.value })} />
            <FormField label="Code postal" value={value.postalCode} onChange={(e) => onChange({ ...value, postalCode: e.target.value })} />
            {event && (
              <EventRoster
                key={event.eventId}
                eventId={event.eventId}
                sectorId={event.sectorId}
                editable={event.status !== "archive"}
                maxima={value.groupMaxima}
                onMaximum={(groupId, maximum) => onChange({ ...value, groupMaxima: { ...value.groupMaxima, [groupId]: maximum } })}
                maximumError={groupMaximum.error}
              />
            )}
          </>
        );
      }}
      onUpdate={save}
      create={{
        buttonLabel: "+ Nouvel événement",
        submitLabel: "Créer l'événement",
        emptyDraft,
        onCreate: ({ status: _status, maxParticipants, groupMaxima: _groupMaxima, ...input }) =>
          create.mutateAsync({ ...input, maxParticipants: Number(maxParticipants) || undefined }),
      }}
    />
    </>
  );
};

export default EventsAdminPage;
