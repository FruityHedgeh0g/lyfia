import React from "react";
import { useGroupActions, useMonGroupe } from "../../features/events/useRoster";
import { MonGroupe, passagerLabel, POSTE_LABELS, RosterEntry, withoutConducteur } from "../../features/events/registrationsApi";
import { EVENT_STATUS_LABELS } from "../../features/events/status";
import { formatDate } from "../../lib/formatDate";
import { journeeLabel } from "../../lib/journees";
import Button from "../../components/atoms/Button/Button";
import Select from "../../components/atoms/Select/Select";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { askWithPassager, confirmRefusal, confirmTakeOut, fullName } from "../../features/events/confirmations";
import Spinner from "../../components/atoms/Spinner/Spinner";
import styles from "./MonGroupePage.module.css";

type GroupEvent = MonGroupe["events"][number];

/**
 * Mon groupe : pour le Groupe que la personne mène (Affectation), chaque
 * Événement Journée par Journée — membres, avec chaque binôme ce jour-là — et
 * ses Demandes de groupe en attente, avec les actions du Chef : accepter,
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

  /** Le Poste, et qui roule avec la personne ce jour-là. */
  const posteLine = (entry: RosterEntry) =>
    entry.conducteur
      ? passagerLabel(entry.conducteur)
      : withoutConducteur(entry)
        ? "Passager · sans conducteur"
        : [POSTE_LABELS[entry.poste], entry.passager && `· passager : ${fullName(entry.passager)}`].filter(Boolean).join(" ");

  const person = (entry: RosterEntry, actions: React.ReactNode) => (
    <li key={entry.personId} className={styles.row}>
      <span className={styles.person}>
        {entry.firstName} {entry.lastName}
        <span className={styles.phone}>{[entry.phone, posteLine(entry)].filter(Boolean).join(" · ")}</span>
      </span>
      <span className={styles.actions}>{actions}</span>
    </li>
  );

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

            {event.journees.map((j) => (
              <div key={j.journee} className={styles.journee}>
                <p className={styles.meta}>
                  {event.journees.length > 1 && <strong>{journeeLabel(j.journee)} · </strong>}
                  Dans le groupe : {j.members.length}
                  {event.maximum !== null ? ` / ${event.maximum}` : ""}
                </p>
                {members(event, j.journee, j.members)}
              </div>
            ))}

            {event.demandes.length > 0 && (
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
                    </>
                  )
                )}
              </ol>
            )}
            {anyFull && event.demandes.length > 0 && (
              <p className={styles.empty}>Maximum atteint : sortez quelqu'un du groupe pour libérer une place, puis choisissez qui accepter.</p>
            )}
          </section>
        );
      })}
    </div>
  );
};

export default MonGroupePage;
