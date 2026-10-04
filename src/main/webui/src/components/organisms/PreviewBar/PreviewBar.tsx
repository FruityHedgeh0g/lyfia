import React from "react";
import { useAuth } from "../../../auth/AuthContext";
import { ROLE_HIERARCHY, ROLE_LABELS, RoleId, roleAtLeast } from "../../../auth/roles";
import { useSectors } from "../../../features/sectors/useSector";
import styles from "./PreviewBar.module.css";

/** Les Rôles qu'un Aperçu peut prendre : tous sous le Super admin. */
const PREVIEW_ROLES = ROLE_HIERARCHY.filter((role) => role !== "super_admin");

/** De Membre à Admin, un Rôle appartient à un Secteur (ADR 0004). */
const hasSecteur = (role: RoleId) => roleAtLeast(role, "membre") && role !== "super_admin";

/**
 * La barre d'Aperçu du Super admin (ADR 0009) : choisir un Rôle, et un Secteur s'il en a un, pour voir le site comme
 * il le verrait, en lecture seule ; puis en sortir d'un clic.
 */
export const PreviewBar: React.FC = () => {
  const { canPreview, preview, setPreview } = useAuth();
  const { data: sectors } = useSectors();
  if (!canPreview) return null;

  const openSectors = (sectors ?? []).filter((s) => !s.closed);
  const choose = (role: string, sectorId?: string) => {
    if (!role) return setPreview(null);
    const r = role as RoleId;
    const sector = hasSecteur(r) ? (openSectors.find((s) => s.sectorId === sectorId) ?? openSectors[0] ?? null) : null;
    setPreview({ role: r, sector: sector && { sectorId: sector.sectorId, name: sector.name } });
  };

  return (
    <div className={`${styles.bar}${preview ? ` ${styles.active}` : ""}`} role="region" aria-label="Aperçu">
      <div className={styles.inner}>
        {preview ? (
          <p className={styles.message}>
            <strong>Aperçu</strong> : le site tel qu'un {ROLE_LABELS[preview.role]}
            {preview.sector ? ` de ${preview.sector.name}` : ""} le voit. Rien ne peut y être fait.
          </p>
        ) : (
          <p className={styles.message}>Voir le site comme un autre rôle, en lecture seule :</p>
        )}
        <label className={styles.field}>
          <span className="sr-only">Aperçu en tant que</span>
          <select value={preview?.role ?? ""} onChange={(e) => choose(e.target.value, preview?.sector?.sectorId)}>
            <option value="">Mon propre affichage</option>
            {PREVIEW_ROLES.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </select>
        </label>
        {preview && hasSecteur(preview.role) && (
          <label className={styles.field}>
            <span className="sr-only">Secteur de l'Aperçu</span>
            <select value={preview.sector?.sectorId ?? ""} onChange={(e) => choose(preview.role, e.target.value)}>
              {openSectors.map((sector) => (
                <option key={sector.sectorId} value={sector.sectorId}>
                  {sector.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {preview && (
          <button type="button" className={styles.leave} onClick={() => setPreview(null)}>
            Quitter l'Aperçu
          </button>
        )}
      </div>
    </div>
  );
};

export default PreviewBar;
