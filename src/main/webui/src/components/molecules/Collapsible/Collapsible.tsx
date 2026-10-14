import React from "react";
import styles from "./Collapsible.module.css";

export interface CollapsibleProps {
  /** Titre toujours visible, sur lequel on clique pour replier ou déplier. */
  title: React.ReactNode;
  children: React.ReactNode;
  /** Déplié au premier affichage (par défaut). */
  defaultOpen?: boolean;
}

/** Section repliable native (`<details>`) : clavier et lecteurs d'écran sans état React. */
export const Collapsible: React.FC<CollapsibleProps> = ({ title, children, defaultOpen = true }) => (
  <details className={styles.collapsible} open={defaultOpen}>
    <summary className={styles.summary}>{title}</summary>
    <div className={styles.content}>{children}</div>
  </details>
);

export default Collapsible;
