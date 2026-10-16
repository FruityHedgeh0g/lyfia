import React, { useEffect, useState } from "react";
import Modal from "../../components/molecules/Modal/Modal";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import Button from "../../components/atoms/Button/Button";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { useGalerieMutations } from "../../features/galerie/useGalerie";
import { photoUrl } from "../../features/galerie/galerieApi";
import { GalleryPhoto, Visibilite, VISIBILITE_LABELS, VISIBILITES } from "../../features/galerie/types";
import { photoCaption } from "../../components/organisms/PhotoGrid/PhotoGrid";
import { Event } from "../../features/events/types";
import { Group } from "../../features/groups/types";
import styles from "./GalleryAdminPage.module.css";

interface PhotoEditDialogProps {
  photo: GalleryPhoto | null;
  onClose: () => void;
  events: Event[];
  groups: Group[];
  /** false pour un Secteur fermé : la photo se regarde sans se modifier. */
  editable: boolean;
}

/** Ce qu'une photo dit d'elle-même (description, Event ou Année, Groupes), sa Visibilité, et sa suppression. */
export const PhotoEditDialog: React.FC<PhotoEditDialogProps> = ({ photo, onClose, events, groups, editable }) => {
  const { change, visibilite, remove } = useGalerieMutations();
  const { confirm, dialog } = useConfirm();
  const [alt, setAlt] = useState("");
  const [eventId, setEventId] = useState("");
  const [annee, setAnnee] = useState("");
  const [groupIds, setGroupIds] = useState<string[]>([]);
  const [shownTo, setShownTo] = useState<Visibilite>("brouillon");

  useEffect(() => {
    if (!photo) return;
    setAlt(photo.alt ?? "");
    setEventId(photo.event?.eventId ?? "");
    setAnnee(String(photo.annee));
    setGroupIds(photo.groups.map((g) => g.groupId));
    setShownTo(photo.visibilite ?? "brouillon");
  }, [photo]);

  const pending = change.isPending || visibilite.isPending || remove.isPending;
  const error = change.error ?? visibilite.error ?? remove.error;
  const close = () => {
    change.reset();
    visibilite.reset();
    remove.reset();
    onClose();
  };

  if (!photo) return dialog;

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    await change.mutateAsync({ photoId: photo.photoId, alt, eventId: eventId || null, annee: eventId ? null : Number(annee), groupIds });
    if (shownTo !== photo.visibilite) await visibilite.mutateAsync({ photoIds: [photo.photoId], visibilite: shownTo });
    close();
  };

  const destroy = async () => {
    const confirmed = await confirm({
      title: "Supprimer la photo ?",
      message: "La photo et ses fichiers sont supprimés pour de bon, par exemple à la demande d'une personne qui y figure.",
      confirmLabel: "Supprimer",
    });
    if (!confirmed) return;
    await remove.mutateAsync(photo.photoId);
    close();
  };

  return (
    <>
      {dialog}
      <Modal isOpen onClose={close} title="Photo de la Galerie">
        <form className={styles.dialogForm} onSubmit={(e) => save(e).catch(() => undefined)} noValidate>
          <img className={styles.preview} src={photoUrl(photo.mediaId, "affichage")} alt={photoCaption(photo)} />
          <FormField label="Description" value={alt} onChange={(e) => setAlt(e.target.value)} disabled={!editable} />
          <Select
            label="Événement"
            value={eventId}
            onChange={setEventId}
            disabled={!editable}
            options={[{ value: "", label: "Aucun : préciser l'année" }, ...events.map((ev) => ({ value: ev.eventId, label: ev.name }))]}
          />
          {!eventId && <FormField label="Année" type="number" min={1990} value={annee} onChange={(e) => setAnnee(e.target.value)} disabled={!editable} />}
          {groups.length > 0 && (
            <fieldset className={styles.groups}>
              <legend className={styles.legend}>Groupes</legend>
              {groups.map((g) => (
                <Checkbox
                  key={g.groupId}
                  label={g.name}
                  checked={groupIds.includes(g.groupId)}
                  disabled={!editable}
                  onChange={(on) => setGroupIds(on ? [...groupIds, g.groupId] : groupIds.filter((id) => id !== g.groupId))}
                />
              ))}
            </fieldset>
          )}
          <Select
            label="Visibilité"
            value={shownTo}
            onChange={(v) => setShownTo(v as Visibilite)}
            disabled={!editable}
            options={VISIBILITES.map((v) => ({ value: v, label: VISIBILITE_LABELS[v] }))}
          />
          {error && <p className={styles.error}>{error.message}</p>}
          {editable && (
            <div className={styles.dialogActions}>
              <Button type="submit" label="Enregistrer" disabled={pending} />
              <Button type="button" label="Supprimer" variant="danger" onClick={() => destroy().catch(() => undefined)} disabled={pending} />
            </div>
          )}
        </form>
      </Modal>
    </>
  );
};

export default PhotoEditDialog;
