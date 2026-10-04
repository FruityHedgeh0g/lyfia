import React from "react";
import { Link } from "react-router-dom";
import Icon from "../../atoms/Icon/Icon";
import { FEATURE_LABELS, FeatureName } from "../../../features/featureFlags/types";
import { entry } from "../../../auth/access";
import styles from "./FeatureNotice.module.css";

export interface FeatureNoticeProps {
  feature: FeatureName;
  /**
   * `suspended` : à la place de ce qu'une personne inscrite ne peut plus faire ; `turned-off` : pour le Super admin,
   * au-dessus de ce qu'il est seul à voir (ADR 0009).
   */
  variant?: "suspended" | "turned-off";
  /** Le message de suspension, propre à ce qui disparaît. */
  message?: string;
}

/** Ce qu'une Fonctionnalité désactivée retire, dit à la place de ce qui a disparu. */
export const FeatureNotice: React.FC<FeatureNoticeProps> = ({ feature, variant = "suspended", message }) =>
  variant === "turned-off" ? (
    <div className={`${styles.notice} ${styles.turnedOff}`} role="status">
      <Icon name="close" size={18} strokeWidth={3} />
      <p>
        <strong>{FEATURE_LABELS[feature]} — désactivée.</strong> Vous seul voyez ceci ; les autres n'y ont pas accès.{" "}
        <Link to={entry("adminFeatureFlags").path}>Fonctionnalités</Link>
      </p>
    </div>
  ) : (
    <p className={styles.notice} role="status">
      {message ?? `${FEATURE_LABELS[feature]} : temporairement suspendu.`}
    </p>
  );

export default FeatureNotice;
