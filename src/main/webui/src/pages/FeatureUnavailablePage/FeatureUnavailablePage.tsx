import React from "react";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import styles from "../NotFoundPage/NotFoundPage.module.css";

/** Une page dont la Fonctionnalité est désactivée, ouverte par un lien direct (ADR 0009). */
export const FeatureUnavailablePage: React.FC = () => (
  <section className={styles.section}>
    <h1 className={styles.title}>Fonctionnalité temporairement indisponible</h1>
    <p className={styles.text}>Cette page est momentanément suspendue. Revenez un peu plus tard.</p>
    <ButtonLink to="/" label="Retour à l'accueil" variant="accent" arrow />
  </section>
);

export default FeatureUnavailablePage;
