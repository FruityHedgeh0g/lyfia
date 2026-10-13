import React, { useCallback, useRef, useState } from "react";
import { createPortal } from "react-dom";
import Icon from "../../atoms/Icon/Icon";
import { useDialog } from "../Modal/useDialog";
import { assetUrl } from "../../../lib/assetUrl";
import styles from "./DevelopersSheet.module.css";

const DEVELOPERS = [
  {
    name: "Julien Peynot",
    avatar: assetUrl("JuAvatarLinkedin.webp"),
    linkedin: "https://www.linkedin.com/in/julien-peynot-91bb69a9/",
  },
  {
    name: "Damien Peynot",
    avatar: assetUrl("DamAvatarLinkedin.webp"),
    linkedin: "https://www.linkedin.com/in/damien-peynot-622128188/",
  },
];

export interface DevelopersSheetProps {
  /** Appelé une fois l'animation de sortie terminée : le parent démonte alors le panneau. */
  onClose: () => void;
}

/**
 * Panneau des développeurs : leurs deux avatars (QR code vers LinkedIn) montent du bas de l'écran.
 * Se referme par le bouton Retour, Échap ou un clic n'importe où hors des avatars.
 */
export const DevelopersSheet: React.FC<DevelopersSheetProps> = ({ onClose }) => {
  const sheetRef = useRef<HTMLDivElement>(null);
  const [closing, setClosing] = useState(false);
  const requestClose = useCallback(() => setClosing(true), []);
  useDialog(true, requestClose, sheetRef);

  return createPortal(
    <div
      className={`${styles.backdrop} ${closing ? styles.closing : ""}`}
      onClick={(e) => {
        if (!(e.target as Element).closest("a, button")) requestClose();
      }}
    >
      <div
        className={styles.sheet}
        role="dialog"
        aria-modal="true"
        aria-label="Les développeurs du site"
        ref={sheetRef}
        tabIndex={-1}
        onAnimationEnd={(e) => {
          if (closing && e.target === e.currentTarget) onClose();
        }}
      >
        <ul className={styles.avatars}>
          {DEVELOPERS.map((dev) => (
            <li key={dev.name}>
              <a className={styles.avatar} href={dev.linkedin} target="_blank" rel="noopener noreferrer">
                <img src={dev.avatar} alt={`${dev.name} sur LinkedIn (nouvel onglet)`} decoding="async" />
              </a>
            </li>
          ))}
        </ul>

        <button type="button" className={styles.back} onClick={requestClose}>
          <span className={styles.backIcon}>
            <Icon name="chevronDown" size={22} />
          </span>
          Retour
        </button>
      </div>
    </div>,
    document.body
  );
};

export default DevelopersSheet;
