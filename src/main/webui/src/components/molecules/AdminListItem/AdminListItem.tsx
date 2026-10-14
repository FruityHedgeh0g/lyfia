import React from "react";
import Icon from "../../atoms/Icon/Icon";
import styles from "./AdminListItem.module.css";

export interface AdminListItemProps {
  title: string;
  subtitle?: string;
  badge?: React.ReactNode;
  /** Visuel affiché avant le titre (ex : miniature d'image). */
  leading?: React.ReactNode;
  /** Contenu additionnel affiché sous le sous-titre, toujours visible (ex : badges de permissions). */
  footer?: React.ReactNode;
  /** Actions supplémentaires affichées avant le chevron (ex : réorganisation). */
  actions?: React.ReactNode;
  editing?: boolean;
  onToggleEdit?: () => void;
  editDisabled?: boolean;
  editDisabledReason?: string;
  children?: React.ReactNode;
}

export const AdminListItem: React.FC<AdminListItemProps> = ({
  title,
  subtitle,
  badge,
  leading,
  footer,
  actions,
  editing,
  onToggleEdit,
  editDisabled,
  editDisabledReason,
  children,
}) => (
  <li className={styles.item}>
    <div className={styles.row}>
      <button
        type="button"
        className={styles.trigger}
        onClick={onToggleEdit}
        disabled={!onToggleEdit || editDisabled}
        aria-expanded={editing}
      >
        {leading && <div className={styles.leading}>{leading}</div>}
        <div className={styles.info}>
          <div className={styles.titleLine}>
            <span className={styles.title}>{title}</span>
            {badge}
          </div>
          {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
          {footer && <div className={styles.footer}>{footer}</div>}
        </div>
      </button>
      <div className={styles.rowActions}>
        {actions}
        {onToggleEdit &&
          (editDisabled ? (
            <span className={styles.disabledNote}>{editDisabledReason}</span>
          ) : (
            // Cible de souris en plus du bouton, qui reste le contrôle pour le clavier et les lecteurs d'écran
            <span className={`${styles.chevron} ${editing ? styles.chevronOpen : ""}`} aria-hidden="true" onClick={onToggleEdit}>
              <Icon name="chevronDown" size={16} strokeWidth={2.5} />
            </span>
          ))}
      </div>
    </div>
    {editing && !editDisabled && <div className={styles.form}>{children}</div>}
  </li>
);

export default AdminListItem;
