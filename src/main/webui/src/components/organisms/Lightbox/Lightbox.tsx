import React, { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import Icon from "../../atoms/Icon/Icon";
import { useDialog } from "../../molecules/Modal/useDialog";
import { GalleryPhoto } from "../../../features/galerie/types";
import { photoUrl } from "../../../features/galerie/galerieApi";
import { photoCaption } from "../PhotoGrid/PhotoGrid";
import styles from "./Lightbox.module.css";

export interface LightboxProps {
  photos: GalleryPhoto[];
  /** La photo ouverte ; null : fermé. */
  index: number | null;
  onIndex: (index: number | null) => void;
}

/**
 * Une photo en grand (variante « affichage », ADR 0014), avec précédente / suivante au clic ou aux flèches du
 * clavier, Échap pour fermer ; sa légende dit ce qu'elle montre, son Event et ses Groupes.
 */
export const Lightbox: React.FC<LightboxProps> = ({ photos, index, onIndex }) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const open = index !== null && index >= 0 && index < photos.length;
  useDialog(open, () => onIndex(null), dialogRef);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft" && index! > 0) onIndex(index! - 1);
      if (e.key === "ArrowRight" && index! < photos.length - 1) onIndex(index! + 1);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, index, photos.length, onIndex]);

  if (!open) return null;
  const photo = photos[index!];
  const details = [photo.event?.name, ...photo.groups.map((g) => g.name)].filter(Boolean).join(" · ");

  return createPortal(
    <div
      className={styles.backdrop}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onIndex(null);
      }}
    >
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-label={photoCaption(photo)} ref={dialogRef} tabIndex={-1}>
        <button type="button" className={styles.close} onClick={() => onIndex(null)} aria-label="Fermer">
          <Icon name="close" size={24} />
        </button>
        <img className={styles.image} src={photoUrl(photo.mediaId, "affichage")} alt={photoCaption(photo)} />
        <p className={styles.caption}>
          <span className={styles.alt}>{photoCaption(photo)}</span>
          {details && <span className={styles.details}>{details}</span>}
          <span className={styles.count}>
            {index! + 1} / {photos.length}
          </span>
        </p>
        {index! > 0 && (
          <button type="button" className={`${styles.nav} ${styles.previous}`} onClick={() => onIndex(index! - 1)} aria-label="Photo précédente">
            ‹
          </button>
        )}
        {index! < photos.length - 1 && (
          <button type="button" className={`${styles.nav} ${styles.next}`} onClick={() => onIndex(index! + 1)} aria-label="Photo suivante">
            ›
          </button>
        )}
      </div>
    </div>,
    document.body
  );
};

export default Lightbox;
