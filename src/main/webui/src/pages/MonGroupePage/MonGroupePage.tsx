import React from "react";
import { useGroupActions, useMonGroupe } from "../../features/events/useRoster";
import { MonGroupe, POSTE_LABELS, RosterEntry, withoutConducteur } from "../../features/events/registrationsApi";
import { EVENT_STATUS_LABELS } from "../../features/events/status";
import { formatDate } from "../../lib/formatDate";
import { journeeLabel } from "../../lib/journees";
import Button from "../../components/atoms/Button/Button";
import Select from "../../components/atoms/Select/Select";
import Badge from "../../components/atoms/Badge/Badge";
import Collapsible from "../../components/molecules/Collapsible/Collapsible";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { askWithPassager, confirmRefusal, confirmTakeOut, fullName } from "../../features/events/confirmations";
import Spinner from "../../components/atoms/Spinner/Spinner";
import EventPhotos from "./EventPhotos";
import styles from "./MonGroupePage.module.css";

type GroupEvent = MonGroupe["events"][number];

/**
 * Mon groupe : pour le Groupe que la personne mène (Affectation), chaque
 * Événement avec d'abord ses Demandes de groupe à valider, à part, puis ses
 * membres Journée par Journée, avec chaque binôme ce jour-là ; et les actions
 * du Chef : accepter,
 * refuser, sortir du groupe, associer un passager à un conducteur du Groupe
 * (ADR 0011). Au maximum du Groupe, les Demandes attendent dans l'ordre
 * d'inscription : sortir quelqu'un libère une place sans accepter personne, le
 * Chef choisit ; une Demande ne s'accepte qu'avec de la place chaque Journée
 * de la personne.
 */
export const MonGroupePage: React.FC = () => {
  const { data, isLoading } = useMonGroupe();
  const { decide, takeOut, pairWith } = useGroupActions();
  const { confirm, dialog } = useConfirm();
  const pending = decide.isPending || takeOut.isPending || pairWith.isPending;
  const error = decide.error ?? takeOut.error ?? pairWith.error;

  if (isLoading) return <Spinner label="Chargement de votre groupe..." />;
  if (!data?.group) return <p className={styles.empty}>Vous ne menez aucun groupe pour le moment.</p>;

  /** Le Poste, en couleur, et qui roule avec la personne ce jour-là ; un passager sans conducteur ressort. */
  const posteLine = (entry: RosterEntry) => {
    const poste = <span className={`${styles.poste} ${styles[entry.poste]}`}>{POSTE_LABELS[entry.poste]}</span>;
    if (entry.conducteur) return <>{poste} de {fullName(entry.conducteur)}</>;
    if (withoutConducteur(entry)) return <>{poste} · <span className={styles.alone}>sans conducteur</span></>;
    return <>{poste}{entry.passager && ` · passager : ${fullName(entry.passager)}`}</>;
  };

  const person = (entry: RosterEntry, actions: React.ReactNode, extra?: string, className = styles.row) => (
    <li key={entry.personId} className={`${className} ${styles[`row_${entry.poste}`]}`}>
      <span className={styles.person}>
        {entry.firstName} {entry.lastName}
        <span className={styles.phone}>
          {entry.phone} · {posteLine(entry)}
          {extra && ` · ${extra}`}
        </span>
      </span>
      <span className={styles.actions}>{actions}</span>
    </li>
  );

  const pendingCount = data.events.reduce((total, event) => total + event.demandes.length, 0);

  /** Les membres d'une Journée, avec la sortie du groupe et l'association d'un passager à un conducteur du Groupe. */
  const members = (event: GroupEvent, journee: string, entries: RosterEntry[]) =>
    entries.length === 0 ? (
      <p className={styles.empty}>Aucun membre {event.journees.length > 1 ? "ce jour-là" : "pour cet événement"}.</p>
    ) : (
      <ul className={styles.list} aria-label={event.journees.length > 1 ? `Membres du groupe — ${journeeLabel(journee)}` : "Membres du groupe"}>
        {entries.map((m) =>
          person(
            m,
            <>
              {m.poste === "passager" && (
                <Select
                  label="Conducteur"
                  value={m.conducteur?.userId ?? ""}
                  disabled={pending}
                  onChange={(conducteurId) =>
                    pairWith.mutate({ eventId: event.eventId, passagerId: m.personId, journee, conducteurId: conducteurId || null })
                  }
                  options={[
                    { value: "", label: "Sans conducteur" },
                    ...entries
                      .filter((c) => c.poste === "conducteur" && (!c.passager || c.passager.userId === m.personId))
                      .map((c) => ({ value: c.personId, label: fullName(c) })),
                  ]}
                />
              )}
              {/* Le Chef reste avec le Groupe qu'il mène (ADR 0010) */}
              {!m.leadsGroup && (
                <Button
                  type="button"
                  label="Sortir du groupe"
                  variant="outline"
                  disabled={pending}
                  onClick={async () => {
                    if (!(await confirmTakeOut(confirm, m))) return;
                    const withPassager = await askWithPassager(confirm, fullName(m), m.passager, "Le sortir aussi du groupe ?");
                    takeOut.mutate({ eventId: event.eventId, personId: m.personId, withPassager });
                  }}
                />
              )}
            </>
          )
        )}
      </ul>
    );

  return (
    <div className={styles.page}>
      {dialog}
      <h2 className={styles.title}>{data.group.name}</h2>
      {data.events.length === 0 && <p className={styles.empty}>Aucun événement ne concerne votre groupe pour le moment.</p>}
      {pendingCount > 0 && (
        <p className={styles.pendingSummary} role="status">
          {pendingCount === 1 ? "1 demande à valider" : `${pendingCount} demandes à valider`}
        </p>
      )}
      {error && <p className={styles.error}>{error.message}</p>}

      {data.events.map((event) => {
        const fullOn = (journee: string) =>
          event.maximum !== null && (event.journees.find((j) => j.journee === journee)?.members.length ?? 0) >= event.maximum;
        const anyFull = event.journees.some((j) => fullOn(j.journee));
        return (
          <section key={event.eventId} className={styles.event} aria-label={event.name}>
            <h3 className={styles.eventTitle}>
              {event.name}
              <span className={styles.meta}>
                {formatDate(event.startDateTime)} · {EVENT_STATUS_LABELS[event.status]}
              </span>
            </h3>

            {event.demandes.length > 0 && (
              <div className={styles.pending}>
                <h4 className={styles.blockTitle}>
                  À valider <Badge label={String(event.demandes.length)} tone="accent" />
                </h4>
                <p className={styles.hint}>
                  Ces personnes demandent à rejoindre le groupe{event.journees.length > 1 ? " ; une demande vaut pour toutes leurs journées" : ""}.
                </p>
                <ol className={styles.list} aria-label="Demandes en attente">
                  {event.demandes.map((d) =>
                    person(
                      d,
                      <>
                        <Button
                          type="button"
                          label="Accepter"
                          disabled={pending || d.journees.some(fullOn)}
                          onClick={() => decide.mutate({ eventId: event.eventId, personId: d.personId, accept: true })}
                        />
                        <Button
                          type="button"
                          label="Refuser"
                          variant="outline"
                          disabled={pending}
                          onClick={async () => {
                            if (await confirmRefusal(confirm, d)) decide.mutate({ eventId: event.eventId, personId: d.personId, accept: false });
                          }}
                        />
                      </>,
                      event.journees.length > 1 ? d.journees.map(journeeLabel).join(", ") : undefined,
                      styles.pendingRow
                    )
                  )}
                </ol>
                {anyFull && (
                  <p className={styles.empty}>Maximum atteint : sortez quelqu'un du groupe pour libérer une place, puis choisissez qui accepter.</p>
                )}
              </div>
            )}

            <div className={styles.members}>
              <h4 className={styles.blockTitle}>Membres</h4>
              {event.journees.map((j) => {
                // Vert tant qu'il reste de la place, rouge au maximum
                const count = (
                  <p className={styles.meta}>
                    Dans le groupe :{" "}
                    <span className={fullOn(j.journee) ? styles.full : styles.room}>
                      {j.members.length}
                      {event.maximum !== null ? ` / ${event.maximum}` : ""}
                    </span>
                  </p>
                );
                return event.journees.length > 1 ? (
                  <Collapsible key={j.journee} title={<strong className={styles.journeeTitle}>{journeeLabel(j.journee)}</strong>}>
                    {count}
                    {members(event, j.journee, j.members)}
                  </Collapsible>
                ) : (
                  <div key={j.journee} className={styles.journee}>
                    {count}
                    {members(event, j.journee, j.members)}
                  </div>
                );
              })}
            </div>

            <EventPhotos eventId={event.eventId} eventName={event.name} />
          </section>
        );
      })}
    </div>
  );
};

export default MonGroupePage;
