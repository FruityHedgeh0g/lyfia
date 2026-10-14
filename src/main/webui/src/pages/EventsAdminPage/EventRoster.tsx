import React, { useState } from "react";
import { useGroupActions, useRoster } from "../../features/events/useRoster";
import { ageOn, GroupRoster, groupKindOf, passagerLabel, POSTE_LABELS, POSTES, RosterEntry, rosterExportUrl, withoutConducteur } from "../../features/events/registrationsApi";
import { useGroups } from "../../features/groups/useGroups";
import { askWithPassager, confirmRefusal, confirmTakeOut, fullName } from "../../features/events/confirmations";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import Button from "../../components/atoms/Button/Button";
import Select from "../../components/atoms/Select/Select";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import FormField from "../../components/molecules/FormField/FormField";
import Spinner from "../../components/atoms/Spinner/Spinner";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import Collapsible from "../../components/molecules/Collapsible/Collapsible";
import { matchesSearch } from "../../lib/textSearch";
import { Journee, journeeLabel } from "../../lib/journees";
import { formatDate } from "../../lib/formatDate";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import styles from "./EventRoster.module.css";

interface EventRosterProps {
  eventId: string;
  /** Secteur de l'Événement : ses Groupes sont proposés pour placer les Participants. */
  sectorId: string;
  /** false une fois l'Événement archivé : la liste est figée. */
  editable: boolean;
  /** Maximums des Groupes saisis, par groupId, envoyés par « Enregistrer » avec la fiche de l'Événement. */
  maxima: Record<string, string>;
  onMaximum: (groupId: string, maximum: string) => void;
  /** Refus d'un maximum à l'enregistrement. */
  maximumError?: Error | null;
}

interface GroupRowProps {
  entry: GroupRoster;
  editable: boolean;
  /** Saisie en cours, absente tant que le maximum enregistré n'est pas touché. */
  maximum: string | undefined;
  onMaximum: (maximum: string) => void;
}

/**
 * Un Groupe à l'Événement : membres sur son maximum, maximum modifiable (vide :
 * sans limite, enregistré avec la fiche de l'Événement) et Demandes en attente dans l'ordre d'inscription, parmi
 * lesquelles on choisit quand une place se libère.
 */
const GroupRow: React.FC<GroupRowProps> = ({ entry, editable, maximum, onMaximum }) => {
  const { group, members, demandes } = entry;

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
            value={maximum ?? (entry.maximum ? String(entry.maximum) : "")}
            onChange={(e) => onMaximum(e.target.value)}
          />
        </span>
      )}
    </li>
  );
};

/**
 * Participants et Liste d'attente d'un Événement, dans l'ordre d'inscription.
 * Le Bureau fait monter à la main (tant que le maximum n'est pas atteint),
 * retire des personnes, décide des Demandes de groupe et place ou sort des
 * Participants d'un Groupe (dans la limite du maximum de chaque Groupe) ;
 * personne ne monte automatiquement, ni dans l'Événement ni dans un Groupe.
 * La liste se lit Journée par Journée (ADR 0011) : chaque Journée a sa Liste
 * d'attente, et le Bureau y associe un passager à un conducteur de son Groupe ;
 * un binôme monte ensemble, et qui retire, place ou sort un conducteur choisit
 * si son passager suit. Les deux listes se replient et une recherche les filtre
 * ensemble, sans appel au serveur.
 */
export const EventRoster: React.FC<EventRosterProps> = ({ eventId, sectorId, editable, maxima, onMaximum, maximumError }) => {
  const [journee, setJournee] = useState<Journee | undefined>(undefined);
  const { roster, moveUp, removePerson, place, changePoste } = useRoster(eventId, journee);
  const { decide, takeOut, pairWith } = useGroupActions();
  const { confirm, dialog } = useConfirm();
  const [search, setSearch] = useState("");
  const { data: groups } = useGroups();
  const exportOn = useFeature("export-liste", sectorId);
  const exportOff = useFeatureOff("export-liste", sectorId);
  const pending =
    moveUp.isPending || removePerson.isPending || place.isPending || changePoste.isPending || decide.isPending || takeOut.isPending || pairWith.isPending;

  if (roster.isLoading) return <Spinner label="Chargement des inscrits..." />;
  if (!roster.data) return null;

  const { participants, waiting, maxParticipants } = roster.data;
  const shownJournee = roster.data.journee;
  const severalJournees = roster.data.journees.length > 1;
  const full = maxParticipants !== null && participants.length >= maxParticipants;
  const error = moveUp.error ?? removePerson.error ?? place.error ?? changePoste.error ?? maximumError ?? decide.error ?? takeOut.error ?? pairWith.error;
  /** Les Groupes du Secteur de la sorte qui va avec le Poste de la personne. */
  const groupOptions = (entry: RosterEntry) => [
    { value: "", label: "Sans groupe" },
    ...(groups ?? [])
      .filter((g) => g.sectorId === sectorId && g.kind === groupKindOf(entry.poste))
      .map((g) => ({ value: g.groupId, label: g.name })),
  ];

  /** Le Poste et qui roule avec la personne ce jour-là, puis le Groupe ou la Demande en attente. */
  const groupLine = (entry: RosterEntry) => {
    const poste = entry.conducteur
      ? passagerLabel(entry.conducteur)
      : withoutConducteur(entry)
        ? "Passager · sans conducteur"
        : [POSTE_LABELS[entry.poste], entry.passager && `· passager : ${fullName(entry.passager)}`].filter(Boolean).join(" ");
    if (entry.group) return `${poste} · ${entry.leadsGroup ? "Chef de groupe" : "Groupe"} : ${entry.group.name}`;
    if (entry.demande?.status === "en_attente") return `${poste} · Demande : ${entry.demande.group.name}`;
    return poste;
  };

  /** Le Chef du Groupe n'est ni retiré ni sorti du groupe : seule l'Affectation le fait (ADR 0010). */
  const removeButton = (entry: RosterEntry) => !entry.leadsGroup && (
    <Button
      type="button"
      label="Retirer"
      variant="outline"
      disabled={pending}
      onClick={async () => {
        const confirmed = await confirm({
          title: severalJournees ? `Retirer du ${journeeLabel(shownJournee)} ?` : "Retirer de l'événement ?",
          message: `${fullName(entry)} sera retiré(e) ${severalJournees ? "de cette journée" : "de l'événement"}. Personne ne prend sa place automatiquement.`,
          confirmLabel: "Retirer",
        });
        if (!confirmed) return;
        const withPassager = await askWithPassager(confirm, fullName(entry), entry.passager, "Retirer aussi son passager ?");
        removePerson.mutate({ personId: entry.personId, withPassager });
      }}
    />
  );

  /** Le Bureau change le Poste, sauf pour le Chef qui mène son Groupe (ADR 0010) et pour un conducteur qui a un passager. */
  const posteSelect = (entry: RosterEntry) =>
    !entry.leadsGroup &&
    !entry.passager && (
      <Select
        label="Poste"
        value={entry.poste}
        disabled={pending}
        onChange={(poste) => poste !== entry.poste && changePoste.mutate({ personId: entry.personId, poste: poste as typeof entry.poste })}
        options={POSTES.map((p) => ({ value: p, label: POSTE_LABELS[p] }))}
      />
    );

  /**
   * Un passager roule derrière un conducteur de son Groupe, en attente ou confirmé comme lui, qui n'a pas encore de
   * passager ce jour-là ; « Sans conducteur » met fin au binôme.
   */
  const conducteurSelect = (entry: RosterEntry, sameList: RosterEntry[]) =>
    entry.poste === "passager" && (
      <Select
        label="Conducteur"
        value={entry.conducteur?.userId ?? ""}
        disabled={pending}
        onChange={(conducteurId) =>
          pairWith.mutate({ eventId, passagerId: entry.personId, journee: shownJournee, conducteurId: conducteurId || null })
        }
        options={[
          { value: "", label: "Sans conducteur" },
          ...sameList
            .filter((c) => c.poste === "conducteur" && c.group?.groupId === entry.group?.groupId)
            .filter((c) => !c.passager || c.passager.userId === entry.personId)
            .map((c) => ({ value: c.personId, label: fullName(c) })),
        ]}
      />
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
            onClick={async () => {
              if (await confirmRefusal(confirm, entry)) decide.mutate({ eventId, personId: entry.personId, accept: false });
            }}
          />
        </>
      )}
      {entry.group && !entry.leadsGroup && (
        <Button
          type="button"
          label="Sortir du groupe"
          variant="outline"
          disabled={pending}
          onClick={async () => {
            if (!(await confirmTakeOut(confirm, entry))) return;
            const withPassager = await askWithPassager(confirm, fullName(entry), entry.passager, "Le sortir aussi du groupe ?");
            takeOut.mutate({ eventId, personId: entry.personId, withPassager });
          }}
        />
      )}
    </>
  );

  /** Un mineur ce jour-là, et qui a approuvé sa décharge (assurance des mineurs). */
  const minorLine = (entry: RosterEntry) => {
    if (!entry.birthDate || ageOn(entry.birthDate, shownJournee) >= 18) return undefined;
    return entry.decharge
      ? `Mineur · décharge signée par ${entry.decharge.signataire} le ${formatDate(entry.decharge.signedAt)}`
      : "Mineur · sans décharge";
  };

  const details = (entry: RosterEntry) => [entry.phone, entry.email, groupLine(entry), minorLine(entry)].filter(Boolean).join(" · ");

  const row = (entry: RosterEntry, actions: React.ReactNode) => (
    <li key={entry.personId} className={styles.row}>
      <span className={styles.person}>
        {entry.firstName} {entry.lastName}
        <span className={styles.phone}>{details(entry)}</span>
      </span>
      {editable && <span className={styles.actions}>{actions}</span>}
    </li>
  );

  /**
   * Une liste repliable, filtrée par la recherche sur tout le texte de ses lignes ;
   * le titre garde le vrai total et compte les résultats quand on cherche.
   */
  const list = (
    title: string,
    label: string,
    entries: RosterEntry[],
    empty: string,
    actions: (entry: RosterEntry) => React.ReactNode
  ) => {
    const shown = entries.filter((e) => matchesSearch(`${e.firstName} ${e.lastName} ${details(e)}`, search));
    const searching = search.trim() !== "";
    return (
      <Collapsible title={<h3 className={styles.title}>{searching ? `${title} · ${shown.length} résultat(s)` : title}</h3>}>
        {entries.length === 0 ? (
          <p className={styles.empty}>{empty}</p>
        ) : shown.length === 0 ? (
          <p className={styles.empty}>Aucun résultat pour « {search.trim()} ».</p>
        ) : (
          <ul className={styles.list} aria-label={label}>
            {shown.map((entry) => row(entry, actions(entry)))}
          </ul>
        )}
      </Collapsible>
    );
  };

  return (
    <section className={styles.roster} aria-label="Inscrits">
      {dialog}
      {exportOn ? (
        <ButtonLink to={rosterExportUrl(eventId)} label="Télécharger la liste (.xlsx)" variant="outline" className={styles.export} download />
      ) : (
        exportOff && <FeatureNotice feature="export-liste" message="Le téléchargement de la liste est temporairement suspendu." />
      )}
      {severalJournees && (
        <Select
          label="Journée"
          value={shownJournee}
          onChange={(selected) => setJournee(selected)}
          options={roster.data.journees.map((j) => ({
            value: j.journee,
            label: `${journeeLabel(j.journee)} · ${j.participants} participant(s)${j.waiting > 0 ? `, ${j.waiting} en attente` : ""}`,
          }))}
        />
      )}
      {participants.length + waiting.length > 0 && (
        <FormField
          label="Rechercher un inscrit"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => {
            // Le roster est dans le formulaire de l'Événement : Entrée ne l'enregistre pas
            if (e.key === "Enter") e.preventDefault();
          }}
        />
      )}

      {list(
        `Participants (${participants.length}${maxParticipants !== null ? ` / ${maxParticipants}` : ""})`,
        "Participants",
        participants,
        "Aucun participant.",
        (p) => (
          <>
            {posteSelect(p)}
            {conducteurSelect(p, participants)}
            {!p.group && groupKindOf(p.poste) && (
              <Select
                label="Groupe"
                value=""
                onChange={async (groupId) => {
                  if (!groupId) return;
                  const withPassager = await askWithPassager(confirm, fullName(p), p.passager, "Le placer aussi dans ce groupe ?");
                  place.mutate({ personId: p.personId, groupId, withPassager });
                }}
                options={groupOptions(p)}
              />
            )}
            {groupActions(p)}
            {removeButton(p)}
          </>
        )
      )}

      {list(`Liste d'attente (${waiting.length})`, "Liste d'attente", waiting, "Personne en attente.", (w) => (
        <>
          {posteSelect(w)}
          {conducteurSelect(w, waiting)}
          {/* Un binôme monte ensemble */}
          <Button type="button" label="Faire monter" disabled={pending || full} onClick={() => moveUp.mutate(w.personId)} />
          {groupActions(w)}
          {removeButton(w)}
        </>
      ))}
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
                maximum={maxima[g.group.groupId]}
                onMaximum={(maximum) => onMaximum(g.group.groupId, maximum)}
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
