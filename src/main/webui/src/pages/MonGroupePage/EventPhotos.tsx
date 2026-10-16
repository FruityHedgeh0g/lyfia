import React, { useState } from "react";
import Button from "../../components/atoms/Button/Button";
import Modal from "../../components/molecules/Modal/Modal";
import FormField from "../../components/molecules/FormField/FormField";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import { useLimites, usePropositions } from "../../features/galerie/useGalerie";
import { photoUrl } from "../../features/galerie/galerieApi";
import { preparePhoto } from "../../features/galerie/resize";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import { photoCaption } from "../../components/organisms/PhotoGrid/PhotoGrid";
import styles from "./MonGroupePage.module.css";

/** Au plus autant de photos proposées d'un coup. */
const MAX_BATCH = 50;

/**
 * Les photos qu'un Chef de groupe propose pour un Event de son Groupe (Propositions de photo) : elles attendent le
 * Bureau, qui les valide ou les refuse ; le Chef les retire tant qu'elles ne sont pas validées.
 */
export const EventPhotos: React.FC<{ eventId: string; eventName: string }> = ({ eventId, eventName }) => {
  const depositOn = useFeature("depot-medias");
  const depositOff = useFeatureOff("depot-medias");
  const { mine, propose, withdraw, invalidate } = usePropositions();
  const [open, setOpen] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [alt, setAlt] = useState("");
  const [progress, setProgress] = useState<{ done: number; failures: string[] } | null>(null);
  const { data: limites } = useLimites(open);
  const sending = progress !== null && progress.done < files.length;
  const pending = (mine.data ?? []).filter((p) => p.event?.eventId === eventId);

  const close = () => {
    if (sending) return;
    setFiles([]);
    setAlt("");
    setProgress(null);
    setOpen(false);
  };

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!limites || files.length === 0 || files.length > MAX_BATCH) return;
    const failures: string[] = [];
    setProgress({ done: 0, failures });
    for (const [index, file] of files.entries()) {
      try {
        await propose.mutateAsync({ photo: await preparePhoto(file, limites), eventId, alt });
      } catch (error) {
        failures.push(`${file.name} : ${error instanceof Error ? error.message : "refusée"}`);
      }
      setProgress({ done: index + 1, failures: [...failures] });
    }
    await invalidate();
    if (failures.length === 0) close();
  };

  return (
    <div className={styles.photos}>
      <h4 className={styles.blockTitle}>Photos</h4>
      {depositOff && <FeatureNotice feature="depot-medias" message="Les propositions de photos sont temporairement suspendues." />}
      {pending.length > 0 && (
        <>
          <p className={styles.hint}>En attente du Bureau, qui les valide ou les refuse :</p>
          <ul className={styles.proposals} aria-label={`Photos proposées pour ${eventName}`}>
            {pending.map((p) => (
              <li key={p.photoId} className={styles.proposal}>
                <img src={photoUrl(p.mediaId, "vignette")} alt={photoCaption(p)} />
                <Button type="button" variant="ghost" label="Retirer" disabled={withdraw.isPending} onClick={() => withdraw.mutate(p.photoId)} />
              </li>
            ))}
          </ul>
        </>
      )}
      {depositOn && <Button type="button" variant="outline" label="Proposer des photos" onClick={() => setOpen(true)} />}

      <Modal isOpen={open} onClose={close} title={`Proposer des photos : ${eventName}`}>
        <form className={styles.proposeForm} onSubmit={send} noValidate>
          <p className={styles.hint}>Les photos sont rattachées à cet événement et à votre groupe. Le Bureau décide qui pourra les voir.</p>
          <FormField
            label={`Photos (${MAX_BATCH} au plus)`}
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp,image/gif"
            onChange={(e) => setFiles(Array.from((e.target as HTMLInputElement).files ?? []))}
            disabled={sending}
          />
          {files.length > MAX_BATCH && <p className={styles.error}>{files.length} photos choisies : {MAX_BATCH} au plus d'un coup.</p>}
          <FormField label="Description commune (facultative)" value={alt} onChange={(e) => setAlt(e.target.value)} disabled={sending} />
          {progress && (
            <p className={styles.hint} role="status">
              {progress.done} / {files.length} photo(s) envoyée(s)
            </p>
          )}
          {progress && progress.failures.length > 0 && (
            <ul className={styles.error} aria-label="Photos refusées">
              {progress.failures.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          )}
          <div className={styles.actions}>
            <Button type="submit" label={sending ? "Envoi…" : "Proposer"} disabled={!limites || files.length === 0 || files.length > MAX_BATCH || sending} />
            <Button type="button" label="Fermer" variant="outline" onClick={close} disabled={sending} />
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default EventPhotos;
