import React, { useId, useRef, useState } from "react";
import Button from "../../atoms/Button/Button";
import Checkbox from "../../atoms/Checkbox/Checkbox";
import { photoUrl } from "../../../features/galerie/galerieApi";
import { useMediaMutations } from "../../../features/medias/useMedias";
import { useGalerieMutations, useLimites } from "../../../features/galerie/useGalerie";
import { preparePhoto } from "../../../features/galerie/resize";
import { useFeature } from "../../../features/featureFlags/useFeatureFlags";
import GalleryPickerDialog from "./GalleryPickerDialog";
import styles from "./ImagePicker.module.css";

/** Où l'image apparaîtra : son cadrage dans l'aperçu, et le format conseillé. */
export type ImageFormat = "banniere" | "carrousel" | "evenement";

const ADVICE: Record<ImageFormat, string> = {
  banniere: "Bannière d'actualité : idéalement 16:9, 1600 px de large.",
  carrousel: "Diapositive du carrousel : idéalement 16:9, 1920 px de large.",
  evenement: "En-tête d'événement : idéalement 16:9, 1600 px de large.",
};

export interface ImagePickerProps {
  label: string;
  /** Le Media choisi ; null : aucun (l'image par défaut du site). */
  value: string | null;
  onChange: (mediaId: string | null) => void;
  format: ImageFormat;
  /** Le Secteur dont la Galerie est proposée ; aucun : le Super admin le choisit. */
  sectorId?: string | null;
  /** Pour l'image d'un Event : ses photos d'abord, et l'Année d'une image versée à la Galerie. */
  event?: { eventId: string; startDateTime: string } | null;
  disabled?: boolean;
}

/**
 * Le sélecteur d'image commun aux bannières d'actualité, au carrousel et aux Events : un aperçu cadré comme l'image
 * apparaîtra, et trois gestes, déposer une image sur place (et, si l'on veut, la verser aussi à la Galerie, Publique),
 * la choisir parmi les photos Publiques de la Galerie, ou la retirer. L'image choisie n'est pas copiée.
 */
export const ImagePicker: React.FC<ImagePickerProps> = ({ label, value, onChange, format, sectorId, event, disabled = false }) => {
  const inputId = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const [toGalerie, setToGalerie] = useState(false);
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const depositOn = useFeature("depot-medias");
  const { upload } = useMediaMutations();
  const { deposit, visibilite, invalidate } = useGalerieMutations();
  const { data: limites } = useLimites(toGalerie);

  const depose = async (file: File) => {
    setError(null);
    setBusy(true);
    try {
      if (toGalerie && limites && sectorId) {
        // Versée à la Galerie : de l'Année en cours, ou de l'Event, et Publique d'emblée puisqu'elle sert au site public
        const photo = await deposit.mutateAsync({
          photo: await preparePhoto(file, limites),
          meta: { sectorId, eventId: event?.eventId, annee: event ? undefined : new Date().getFullYear(), groupIds: [] },
        });
        await visibilite.mutateAsync({ photoIds: [photo.photoId], visibilite: "publique" });
        await invalidate();
        onChange(photo.mediaId);
      } else {
        const media = await upload.mutateAsync({ file, alt: file.name.replace(/\.[^.]+$/, "") });
        onChange(media.mediaId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "L'image n'a pas pu être déposée.");
    } finally {
      setBusy(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  return (
    <div className={styles.picker}>
      <span className={styles.label} id={`${inputId}-label`}>
        {label}
      </span>
      <div className={`${styles.frame} ${styles[format]}`} aria-labelledby={`${inputId}-label`}>
        {value ? <img src={photoUrl(value, "affichage")} alt="Aperçu de l'image choisie" /> : <span className={styles.none}>Aucune image</span>}
      </div>
      <p className={styles.hint}>{ADVICE[format]}</p>
      <div className={styles.actions}>
        {depositOn && (
          <>
            <input
              ref={fileInput}
              id={inputId}
              className="sr-only"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              aria-label={`Déposer une image : ${label}`}
              disabled={disabled || busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) depose(file);
              }}
            />
            <Button type="button" variant="outline" label={busy ? "Envoi…" : "Déposer une image"} disabled={disabled || busy} onClick={() => fileInput.current?.click()} />
          </>
        )}
        <Button type="button" variant="outline" label="Choisir dans la Galerie" disabled={disabled || busy} onClick={() => setPicking(true)} />
        {value && <Button type="button" variant="ghost" label="Retirer" disabled={disabled || busy} onClick={() => onChange(null)} />}
      </div>
      {depositOn && sectorId && (
        <Checkbox label="L'ajouter aussi à la Galerie (Publique)" checked={toGalerie} onChange={setToGalerie} disabled={disabled || busy} />
      )}
      {error && <p className={styles.error}>{error}</p>}
      <GalleryPickerDialog isOpen={picking} onClose={() => setPicking(false)} onPick={onChange} sectorId={sectorId} event={event} />
    </div>
  );
};

export default ImagePicker;
