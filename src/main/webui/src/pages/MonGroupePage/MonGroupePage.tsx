import React from "react";
import { useGroupActions, useMonGroupe } from "../../features/events/useRoster";
import { passagerLabel, POSTE_LABELS, RosterEntry } from "../../features/events/registrationsApi";
import { EVENT_STATUS_LABELS } from "../../features/events/status";
import { formatDate } from "../../lib/formatDate";
import Button from "../../components/atoms/Button/Button";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { confirmRefusal, confirmTakeOut } from "../../features/events/confirmations";
import Spinner from "../../components/atoms/Spinner/Spinner";
import styles from "./MonGroupePage.module.css";

/**
 * Mon groupe : pour le Groupe que la personne mène (Affectation), la liste de
 * chaque Événement — membres et Demandes de groupe en attente — avec les
 * actions du Chef : accepter, refuser, sortir du groupe. Au maximum du Groupe,
 * les Demandes attendent dans l'ordre d'inscription : sortir quelqu'un libère
 * une place sans accepter personne, le Chef choisit.
 */
export const MonGroupePage: React.FC = () => {
  const { data, isLoading } = useMonGroupe();
  const { decide, takeOut } = useGroupActions();
  const { confirm, dialog } = useConfirm();
  const pending = decide.isPending || takeOut.isPending;
  const error = decide.error ?? takeOut.error;

  if (isLoading) return <Spinner label="Chargement de votre groupe..." />;
  if (!data?.group) return <p className={styles.empty}>Vous ne menez aucun groupe pour le moment.</p>;

  const person = (entry: RosterEntry, actions: React.ReactNode) => (
    <li key={entry.personId} className={styles.row}>
      <span className={styles.person}>
        {entry.firstName} {entry.lastName}
        <span className={styles.phone}>
          {[
            entry.phone,
            entry.conducteur
              ? passagerLabel(entry.conducteur)
              : [POSTE_LABELS[entry.poste], entry.passagers > 0 && `+ ${entry.passagers} passager(s)`].filter(Boolean).join(" "),
          ]
            .filter(Boolean)
            .join(" · ")}
        </span>
      </span>
      <span className={styles.actions}>{actions}</span>
    </li>
  );

  return (
    <div className={styles.page}>
      {dialog}
      <h2 className={styles.title}>{data.group.name}</h2>
      {data.events.length === 0 && <p className={styles.empty}>Aucun événement ne concerne votre groupe pour le moment.</p>}
      {error && <p className={styles.error}>{error.message}</p>}

      {data.events.map((event) => {
        const full = event.maximum !== null && event.members.length >= event.maximum;
        return (
          <section key={event.eventId} className={styles.event} aria-label={event.name}>
            <h3 className={styles.eventTitle}>
              {event.name}
              <span className={styles.meta}>
                {formatDate(event.startDateTime)} · {EVENT_STATUS_LABELS[event.status]}
              </span>
            </h3>
            <p className={styles.meta}>
              Dans le groupe : {event.members.length}
              {event.maximum !== null ? ` / ${event.maximum}` : ""}
            </p>

            {event.members.length === 0 ? (
              <p className={styles.empty}>Aucun membre pour cet événement.</p>
            ) : (
              <ul className={styles.list} aria-label="Membres du groupe">
                {event.members.map((m) =>
                  person(
                    m,
                    // Un passager sort du groupe avec son conducteur ; le Chef reste avec le Groupe qu'il mène (ADR 0010)
                    !m.conducteur && !m.leadsGroup && (
                      <Button
                        type="button"
                        label="Sortir du groupe"
                        variant="outline"
                        disabled={pending}
                        onClick={async () => {
                          if (await confirmTakeOut(confirm, m)) takeOut.mutate({ eventId: event.eventId, personId: m.personId });
                        }}
                      />
                    )
                  )
                )}
              </ul>
            )}

            {event.demandes.length > 0 && (
              <ol className={styles.list} aria-label="Demandes en attente">
                {event.demandes.map((d) =>
                  person(
                    d,
                    <>
                      <Button
                        type="button"
                        label="Accepter"
                        disabled={pending || full}
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
            {full && event.demandes.length > 0 && (
              <p className={styles.empty}>Maximum atteint : sortez quelqu'un du groupe pour libérer une place, puis choisissez qui accepter.</p>
            )}
          </section>
        );
      })}
    </div>
  );
};

export default MonGroupePage;
