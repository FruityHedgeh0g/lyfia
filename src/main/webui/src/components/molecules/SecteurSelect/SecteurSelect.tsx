import React, { useEffect } from "react";
import { useSectors } from "../../../features/sectors/useSector";
import { useSecteurChoice } from "../../../features/sectors/secteurChoice";
import styles from "./SecteurSelect.module.css";

/**
 * Le choix de Secteur du site public : il filtre les Événements, les Actualités et le Carrousel. Un Secteur fermé
 * n'est jamais proposé ; un choix qui ne correspond plus à un Secteur ouvert revient à « Tous les secteurs ».
 * Avec un seul Secteur ouvert, il n'y a rien à choisir : le choix n'est pas affiché.
 */
export const SecteurSelect: React.FC = () => {
  const { data: sectors, isSuccess } = useSectors();
  const { sectorId, setSectorId } = useSecteurChoice();
  const open = (sectors ?? []).filter((s) => !s.closed);

  useEffect(() => {
    if (isSuccess && sectorId && !open.some((s) => s.sectorId === sectorId)) setSectorId("");
  }, [isSuccess, sectorId, open, setSectorId]);

  if (open.length <= 1) return null;

  return (
    <label className={styles.field}>
      <span className="sr-only">Secteur</span>
      <select value={sectorId} onChange={(e) => setSectorId(e.target.value)}>
        <option value="">Tous les secteurs</option>
        {open.map((sector) => (
          <option key={sector.sectorId} value={sector.sectorId}>
            {sector.name}
          </option>
        ))}
      </select>
    </label>
  );
};

export default SecteurSelect;
