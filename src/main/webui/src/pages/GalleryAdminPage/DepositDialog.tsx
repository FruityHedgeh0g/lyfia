import React, { useState } from "react";
import Modal from "../../components/molecules/Modal/Modal";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import Button from "../../components/atoms/Button/Button";
import { useGalerieMutations, useLimites } from "../../features/galerie/useGalerie";
import { preparePhoto } from "../../features/galerie/resize";
import { Event } from "../../features/events/types";
import { Group } from "../../features/groups/types";
import styles from "./GalleryAdminPage.module.css";

/** Au plus autant de photos par dépôt. */
export const MAX_BATCH = 50;

interface DepositDialogProps {
  isOpen: boolean;
  onClose: () => void;
  /** Le Secteur des photos ; le Super admin le nomme, le Bureau dépose dans le sien. */
  sectorId: string;
  namesSector: boolean;
  events: Event[];
  groups: Group[];
  /** Le lot envoyé : l'Année de ses photos, pour ouvrir leur Album. */
  onDeposited: (annee: number) => void;
}

/**
 * Le dépôt en lot (jusqu'à 50 photos) avec ce qu'elles ont en commun : leur Event (qui donne l'Année) ou leur Année,
 * leurs Groupes, une description. Chaque photo est préparée par le navigateur puis envoyée à son tour (ADR 0014) ;
 * elles arrivent en Brouillon.
 */
export const DepositDialog: React.FC<DepositDialogProps> = ({ isOpen, onClose, sectorId, namesSector, events, groups, onDeposited }) => {
  const { data: limites } = useLimites(isOpen);
  const { deposit, invalidate } = useGalerieMutations();
  const [files, setFiles] = useState<File[]>([]);
  const [eventId, setEventId] = useState("");
  const [annee, setAnnee] = useState(String(new Date().getFullYear()));
  const [groupIds, setGroupIds] = useState<string[]>([]);
  const [alt, setAlt] = useState("");
  const [progress, setProgress] = useState<{ done: number; failures: string[] } | null>(null);
  const sending = progress !== null && progress.done < files.length;

  const close = () => {
    if (sending) return;
    setFiles([]);
    setEventId("");
    setGroupIds([]);
    setAlt("");
    setProgress(null);
    onClose();
  };

  const event = events.find((e) => e.eventId === eventId);
  const year = event ? new Date(event.startDateTime).getFullYear() : Number(annee);
  const ready = files.length > 0 && files.length <= MAX_BATCH && Boolean(limites) && (event || (year >= 1990 && year <= new Date().getFullYear() + 1));

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ready || !limites) return;
    const failures: string[] = [];
    setProgress({ done: 0, failures });
    for (const [index, file] of files.entries()) {
      try {
        const photo = await preparePhoto(file, limites);
        await deposit.mutateAsync({
          photo,
          meta: { sectorId: namesSector ? sectorId : undefined, eventId: event?.eventId, annee: event ? undefined : year, groupIds, alt },
        });
      } catch (error) {
        failures.push(`${file.name} : ${error instanceof Error ? error.message : "refusée"}`);
      }
      setProgress({ done: index + 1, failures: [...failures] });
    }
    await invalidate();
    onDeposited(year);
    if (failures.length === 0) close();
  };

  return (
    <Modal isOpen={isOpen} onClose={close} title="Déposer des photos">
      <form className={styles.dialogForm} onSubmit={send} noValidate>
        <FormField
          label={`Photos (${MAX_BATCH} au plus)`}
          type="file"
          multiple
          accept="image/jpeg,image/png,image/webp,image/gif"
          onChange={(e) => setFiles(Array.from((e.target as HTMLInputElement).files ?? []))}
          disabled={sending}
        />
        {files.length > MAX_BATCH && <p className={styles.error}>{files.length} photos choisies : {MAX_BATCH} au plus par dépôt.</p>}
        {limites && (
          <p className={styles.muted}>
            Une photo de plus de {limites.maxPx} px ou de {limites.maxMo} Mo est réduite avant l'envoi.
          </p>
        )}
        <Select
          label="Événement"
          value={eventId}
          onChange={setEventId}
          disabled={sending}
          options={[{ value: "", label: "Aucun : préciser l'année" }, ...events.map((e) => ({ value: e.eventId, label: e.name }))]}
        />
        {!event && (
          <FormField label="Année" type="number" min={1990} value={annee} onChange={(e) => setAnnee(e.target.value)} disabled={sending} />
        )}
        {groups.length > 0 && (
          <fieldset className={styles.groups}>
            <legend className={styles.legend}>Groupes</legend>
            {groups.map((g) => (
              <Checkbox
                key={g.groupId}
                label={g.name}
                checked={groupIds.includes(g.groupId)}
                disabled={sending}
                onChange={(on) => setGroupIds(on ? [...groupIds, g.groupId] : groupIds.filter((id) => id !== g.groupId))}
              />
            ))}
          </fieldset>
        )}
        <FormField label="Description commune (facultative)" value={alt} onChange={(e) => setAlt(e.target.value)} disabled={sending} />
        {progress && (
          <p className={styles.muted} role="status">
            {progress.done} / {files.length} photo(s) envoyée(s)
          </p>
        )}
        {progress && progress.failures.length > 0 && (
          <ul className={styles.failures} aria-label="Photos refusées">
            {progress.failures.map((f) => (
              <li key={f}>{f}</li>
            ))}
          </ul>
        )}
        <div className={styles.dialogActions}>
          <Button type="submit" label={sending ? "Envoi…" : "Déposer"} disabled={!ready || sending} />
          <Button type="button" label="Fermer" variant="outline" onClick={close} disabled={sending} />
        </div>
      </form>
    </Modal>
  );
};

export default DepositDialog;
