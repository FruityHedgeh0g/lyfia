import React, { useState } from "react";
import { useGroupActions, useRoster } from "../../features/events/useRoster";
import { GroupRoster, passagerLabel, RosterEntry, rosterExportUrl } from "../../features/events/registrationsApi";
import { useGroups } from "../../features/groups/useGroups";
import Button from "../../components/atoms/Button/Button";
import Select from "../../components/atoms/Select/Select";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import FormField from "../../components/molecules/FormField/FormField";
import Spinner from "../../components/atoms/Spinner/Spinner";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import styles from "./EventRoster.module.css";

interface EventRosterProps {
  eventId: string;
  /** Secteur de l'Événement : ses Groupes sont proposés pour placer les Participants. */
  sectorId: string;
  /** false une fois l'Événement archivé : la liste est figée. */
  editable: boolean;
}

interface GroupRowProps {
  entry: GroupRoster;
  editable: boolean;
  disabled: boolean;
  onMaximum: (maximum: number | null) => void;
}

/**
 * Un Groupe à l'Événement : membres sur son maximum, maximum modifiable (vide :
 * sans limite) et Demandes en attente dans l'ordre d'inscription, parmi
 * lesquelles on choisit quand une place se libère.
 */
const GroupRow: React.FC<GroupRowProps> = ({ entry, editable, disabled, onMaximum }) => {
  const [maximum, setMaximum] = useState(entry.maximum ? String(entry.maximum) : "");
  const { group, members, demandes } = entry;
  const apply = () => onMaximum(Number(maximum) || null);

  return (
    <li className={styles.row}>
      <span className={styles.person}>
        {group.name}
        <span className={styles.phone}>
          {members.length}
          {entry.maximum !== null ? ` / ${entry.maximum}` : ""} dans le groupe
        </span>
        {demandes.length > 0 && (
          <ol className={styles.queue} aria-label={`Liste d'attente de ${group.name}`}>
            {demandes.map((d) => (
              <li key={d.personId}>
                {d.firstName} {d.lastName}
              </li>
            ))}
          </ol>
        )}
      </span>
      {editable && (
        <span className={styles.actions}>
          <FormField
            label="Maximum"
            type="number"
            min={0}
            value={maximum}
            onChange={(e) => setMaximum(e.target.value)}
            onKeyDown={(e) => {
              // Le roster est dans le formulaire de l'Événement : Entrée applique le maximum sans l'enregistrer
              if (e.key === "Enter") {
                e.preventDefault();
                apply();
              }
            }}
          />
          <Button type="button" label="Appliquer" variant="outline" disabled={disabled} onClick={apply} />
        </span>
      )}
    </li>
  );
};

/**
 * Participants et Liste d'attente d'un Événement, dans l'ordre d'inscription.
 * Le Bureau fait monter à la main (tant que le maximum n'est pas atteint),
 * retire des personnes, décide des Demandes de groupe et place ou sort des
 * Participants d'un Groupe (dans la limite du maximum de chaque Groupe) ; un
 * passager suit son pilote, seul son retrait se fait à part ;
 * personne ne monte automatiquement, ni dans l'Événement ni dans un Groupe.
 */
export const EventRoster: React.FC<EventRosterProps> = ({ eventId, sectorId, editable }) => {
  const { roster, moveUp, removePerson, place, setMaximum } = useRoster(eventId);
  const { decide, takeOut } = useGroupActions();
  const { data: groups } = useGroups();
  const exportOn = useFeature("export-liste");
  const exportOff = useFeatureOff("export-liste");
  const pending =
    moveUp.isPending || removePerson.isPending || place.isPending || setMaximum.isPending || decide.isPending || takeOut.isPending;

  if (roster.isLoading) return <Spinner label="Chargement des inscrits..." />;
  if (!roster.data) return null;

  const { participants, waiting, maxParticipants } = roster.data;
  const full = maxParticipants !== null && participants.length >= maxParticipants;
  const error = moveUp.error ?? removePerson.error ?? place.error ?? setMaximum.error ?? decide.error ?? takeOut.error;
  const groupOptions = [
    { value: "", label: "Sans groupe" },
    ...(groups ?? []).filter((g) => g.sectorId === sectorId).map((g) => ({ value: g.groupId, label: g.name })),
  ];

  const groupLine = (entry: RosterEntry) => {
    const passager = entry.pilote ? passagerLabel(entry.pilote) : entry.passagers > 0 && `+ ${entry.passagers} passager(s)`;
    if (entry.group) return [passager, `Groupe : ${entry.group.name}`].filter(Boolean).join(" · ");
    if (passager) return passager;
    if (entry.demande?.status === "en_attente") return `Demande : ${entry.demande.group.name}`;
    return undefined;
  };

  const removeButton = (entry: RosterEntry) => (
    <Button type="button" label="Retirer" variant="outline" disabled={pending} onClick={() => removePerson.mutate(entry.personId)} />
  );

  /** Décision sur une Demande en attente, et sortie du Groupe pour qui y roule. */
  const groupActions = (entry: RosterEntry) => (
    <>
      {entry.demande?.status === "en_attente" && (
        <>
          <Button type="button" label="Accepter" disabled={pending} onClick={() => decide.mutate({ eventId, personId: entry.personId, accept: true })} />
          <Button
            type="button"
            label="Refuser"
            variant="outline"
            disabled={pending}
            onClick={() => decide.mutate({ eventId, personId: entry.personId, accept: false })}
          />
        </>
      )}
      {entry.group && !entry.pilote && (
        <Button type="button" label="Sortir du groupe" variant="outline" disabled={pending} onClick={() => takeOut.mutate({ eventId, personId: entry.personId })} />
      )}
    </>
  );

  const row = (entry: RosterEntry, actions: React.ReactNode) => (
    <li key={entry.personId} className={styles.row}>
      <span className={styles.person}>
        {entry.firstName} {entry.lastName}
        <span className={styles.phone}>{[entry.phone, groupLine(entry)].filter(Boolean).join(" · ")}</span>
      </span>
      {editable && <span className={styles.actions}>{actions}</span>}
    </li>
  );

  return (
    <section className={styles.roster} aria-label="Inscrits">
      {exportOn ? (
        <ButtonLink to={rosterExportUrl(eventId)} label="Télécharger la liste (.xlsx)" variant="outline" className={styles.export} download />
      ) : (
        exportOff && <FeatureNotice feature="export-liste" message="Le téléchargement de la liste est temporairement suspendu." />
      )}
      <h3 className={styles.title}>
        Participants ({participants.length}
        {maxParticipants !== null ? ` / ${maxParticipants}` : ""})
      </h3>
      {participants.length === 0 ? (
        <p className={styles.empty}>Aucun participant.</p>
      ) : (
        <ul className={styles.list} aria-label="Participants">
          {participants.map((p) =>
            row(
              p,
              <>
                {!p.group && !p.pilote && (
                  <Select
                    label="Groupe"
                    value=""
                    onChange={(groupId) => groupId && place.mutate({ personId: p.personId, groupId })}
                    options={groupOptions}
                  />
                )}
                {groupActions(p)}
                {removeButton(p)}
              </>
            )
          )}
        </ul>
      )}

      <h3 className={styles.title}>Liste d'attente ({waiting.length})</h3>
      {waiting.length === 0 ? (
        <p className={styles.empty}>Personne en attente.</p>
      ) : (
        <ul className={styles.list} aria-label="Liste d'attente">
          {waiting.map((w) =>
            row(
              w,
              <>
                {!w.pilote && (
                  <Button type="button" label="Faire monter" disabled={pending || full} onClick={() => moveUp.mutate(w.personId)} />
                )}
                {groupActions(w)}
                {removeButton(w)}
              </>
            )
          )}
        </ul>
      )}
      {full && waiting.length > 0 && editable && <p className={styles.empty}>Maximum atteint : libérez une place pour faire monter quelqu'un.</p>}

      {roster.data.groups.length > 0 && (
        <>
          <h3 className={styles.title}>Groupes</h3>
          <ul className={styles.list} aria-label="Groupes">
            {roster.data.groups.map((g) => (
              <GroupRow
                key={g.group.groupId}
                entry={g}
                editable={editable}
                disabled={pending}
                onMaximum={(maximum) => setMaximum.mutate({ groupId: g.group.groupId, maximum })}
              />
            ))}
          </ul>
        </>
      )}
      {error && <p className={styles.error}>{error.message}</p>}
    </section>
  );
};

export default EventRoster;
