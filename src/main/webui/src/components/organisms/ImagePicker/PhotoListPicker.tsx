import React, { useId, useRef, useState } from "react";
import Button from "../../atoms/Button/Button";
import { photoUrl } from "../../../features/galerie/galerieApi";
import { useMediaMutations } from "../../../features/medias/useMedias";
import { useFeature } from "../../../features/featureFlags/useFeatureFlags";
import GalleryPickerDialog from "./GalleryPickerDialog";
import styles from "./ImagePicker.module.css";

/** Au plus autant de photos dans une actualité. */
export const MAX_PHOTOS = 12;

export interface PhotoListPickerProps {
  label: string;
  /** Les Media choisis, dans leur ordre. */
  value: string[];
  onChange: (mediaIds: string[]) => void;
  /** Le Secteur dont la Galerie est proposée ; aucun : le Super admin le choisit. */
  sectorId?: string | null;
  disabled?: boolean;
}

/**
 * Plusieurs photos, dans un ordre choisi (les « Photos de l'actualité », 12 au plus) : prises parmi les photos
 * Publiques de la Galerie ou déposées sur place, déplacées et retirées une à une.
 */
export const PhotoListPicker: React.FC<PhotoListPickerProps> = ({ label, value, onChange, sectorId, disabled = false }) => {
  const inputId = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const [picking, setPicking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const depositOn = useFeature("depot-medias");
  const { upload } = useMediaMutations();
  const full = value.length >= MAX_PHOTOS;

  const add = (mediaId: string) => {
    if (!value.includes(mediaId) && value.length < MAX_PHOTOS) onChange([...value, mediaId]);
  };
  const move = (index: number, by: number) => {
    const next = [...value];
    [next[index], next[index + by]] = [next[index + by], next[index]];
    onChange(next);
  };

  const depose = async (files: File[]) => {
    setError(null);
    setBusy(true);
    const added: string[] = [];
    try {
      for (const file of files.slice(0, MAX_PHOTOS - value.length)) {
        const media = await upload.mutateAsync({ file, alt: file.name.replace(/\.[^.]+$/, "") });
        added.push(media.mediaId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Une photo n'a pas pu être déposée.");
    } finally {
      onChange([...value, ...added]);
      setBusy(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  return (
    <div className={styles.picker}>
      <span className={styles.label}>
        {label} ({value.length} / {MAX_PHOTOS})
      </span>
      {value.length > 0 ? (
        <ol className={styles.photoList} aria-label={label}>
          {value.map((mediaId, index) => (
            <li key={mediaId} className={styles.photoListItem}>
              <img src={photoUrl(mediaId, "vignette")} alt={`Photo ${index + 1}`} />
              <span className={styles.photoListActions}>
                <button type="button" aria-label={`Avancer la photo ${index + 1}`} disabled={disabled || index === 0} onClick={() => move(index, -1)}>
                  ←
                </button>
                <button
                  type="button"
                  aria-label={`Reculer la photo ${index + 1}`}
                  disabled={disabled || index === value.length - 1}
                  onClick={() => move(index, 1)}
                >
                  →
                </button>
                <button type="button" aria-label={`Retirer la photo ${index + 1}`} disabled={disabled} onClick={() => onChange(value.filter((id) => id !== mediaId))}>
                  ✕
                </button>
              </span>
            </li>
          ))}
        </ol>
      ) : (
        <p className={styles.hint}>Aucune photo : elles s'affichent en grille sous le texte, et s'ouvrent en grand.</p>
      )}
      <div className={styles.actions}>
        {depositOn && (
          <>
            <input
              ref={fileInput}
              id={inputId}
              className="sr-only"
              type="file"
              multiple
              accept="image/jpeg,image/png,image/webp,image/gif"
              aria-label={`Déposer des photos : ${label}`}
              disabled={disabled || busy || full}
              onChange={(e) => depose(Array.from(e.target.files ?? []))}
            />
            <Button
              type="button"
              variant="outline"
              label={busy ? "Envoi…" : "Déposer des photos"}
              disabled={disabled || busy || full}
              onClick={() => fileInput.current?.click()}
            />
          </>
        )}
        <Button type="button" variant="outline" label="Choisir dans la Galerie" disabled={disabled || busy || full} onClick={() => setPicking(true)} />
      </div>
      {error && <p className={styles.error}>{error}</p>}
      <GalleryPickerDialog isOpen={picking} onClose={() => setPicking(false)} onPick={add} sectorId={sectorId} />
    </div>
  );
};

export default PhotoListPicker;
