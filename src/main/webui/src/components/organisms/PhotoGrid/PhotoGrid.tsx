import React from "react";
import { GalleryPhoto } from "../../../features/galerie/types";
import { photoUrl } from "../../../features/galerie/galerieApi";
import styles from "./PhotoGrid.module.css";

/** Ce qu'une photo montre, pour sa légende et les lecteurs d'écran : sa description, sinon son Event. */
export const photoCaption = (photo: GalleryPhoto) => photo.alt || photo.event?.name || "Photo";

export interface PhotoGridProps {
  photos: GalleryPhoto[];
  /** Ouvre la photo en grand. */
  onOpen: (index: number) => void;
  /** Ce que l'administration ajoute sur une vignette : sélection, Visibilité. */
  overlay?: (photo: GalleryPhoto) => React.ReactNode;
  /** La première photo à la une, sur 2 × 2 cases (la Galerie publique). */
  featured?: boolean;
}

/** Mosaïque de vignettes (ADR 0014) : on n'y charge que la variante « vignette ». */
export const PhotoGrid: React.FC<PhotoGridProps> = ({ photos, onOpen, overlay, featured = false }) => (
  <ul className={`${styles.grid} ${featured ? styles.featured : ""}`}>
    {photos.map((photo, index) => (
      <li className={styles.item} key={photo.photoId}>
        <button type="button" className={styles.open} onClick={() => onOpen(index)} aria-label={`Agrandir : ${photoCaption(photo)}`}>
          <img className={styles.image} src={photoUrl(photo.mediaId, "vignette")} alt={photoCaption(photo)} loading="lazy" />
          <span className={styles.caption} aria-hidden="true">
            {photoCaption(photo)}
          </span>
        </button>
        {overlay && <div className={styles.overlay}>{overlay(photo)}</div>}
      </li>
    ))}
  </ul>
);

export default PhotoGrid;
