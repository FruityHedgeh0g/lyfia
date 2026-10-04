import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../../auth/AuthContext";
import { ROLE_HIERARCHY, ROLE_LABELS, RoleId, roleAtLeast } from "../../../auth/roles";
import { useSectors } from "../../../features/sectors/useSector";
import { apiFetch } from "../../../lib/http";
import styles from "./DevPersonaSwitcher.module.css";

/** Les personas : tous les Rôles d'une personne inscrite (Visiteur : se déconnecter). */
const PERSONAS = ROLE_HIERARCHY.filter((role) => role !== "visiteur");

const hasSecteur = (role: RoleId) => roleAtLeast(role, "membre") && role !== "super_admin";

/**
 * Là où l'API l'offre seulement (lyfia.dev-personas, profils dev et test ; jamais en production) : la personne connectée
 * prend n'importe quel Rôle, et un Secteur s'il en a un, pour essayer le site sans autre compte Keycloak. Son Rôle
 * change vraiment en base (PUT /api/dev/persona) : tout ce que ce Rôle peut faire devient faisable.
 */
export const DevPersonaSwitcher: React.FC = () => {
  const { user, preview } = useAuth();
  const queryClient = useQueryClient();
  const { data: sectors } = useSectors();
  const [error, setError] = useState(false);
  // 404 hors développement : pas de choix de persona
  const offered = useQuery({
    queryKey: ["dev-personas"],
    queryFn: () => apiFetch<void>("/api/dev/persona").then(() => true),
    enabled: Boolean(user),
    staleTime: Infinity,
    retry: false,
  });
  if (!user || preview || offered.data !== true) return null;

  const openSectors = (sectors ?? []).filter((s) => !s.closed);
  const become = async (role: RoleId, sectorId?: string) => {
    try {
      setError(false);
      await apiFetch("/api/dev/persona", { method: "PUT", body: JSON.stringify({ role, sectorId: hasSecteur(role) ? sectorId : undefined }) });
      // Qui est connecté, et donc tout ce que l'API renvoie, vient de changer
      await queryClient.invalidateQueries();
    } catch {
      setError(true);
    }
  };

  return (
    <span className={styles.switcher}>
      <label>
        <span className={styles.label}>Persona (dev)</span>
        <select value={user.role} onChange={(e) => become(e.target.value as RoleId, user.sector?.sectorId)}>
          {PERSONAS.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </select>
      </label>
      {hasSecteur(user.role) && openSectors.length > 0 && (
        <label>
          <span className="sr-only">Secteur du persona</span>
          <select value={user.sector?.sectorId ?? ""} onChange={(e) => become(user.role, e.target.value)}>
            {openSectors.map((sector) => (
              <option key={sector.sectorId} value={sector.sectorId}>
                {sector.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {error && <span role="alert">Changement impossible</span>}
    </span>
  );
};

export default DevPersonaSwitcher;
