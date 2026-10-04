import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../../auth/AuthContext";
import { ROLE_HIERARCHY, ROLE_LABELS, RoleId, roleAtLeast } from "../../../auth/roles";
import { useSectors } from "../../../features/sectors/useSector";
import { apiFetch } from "../../../lib/http";
import styles from "./DevPersonaSwitcher.module.css";

interface Persona {
  role: RoleId;
  sectorId: string | null;
}

/** Les personas : tous les Rôles d'une personne inscrite ; « Visiteur » quitte le persona. */
const PERSONAS = ROLE_HIERARCHY.filter((role) => role !== "visiteur");

const hasSecteur = (role: RoleId) => roleAtLeast(role, "membre") && role !== "super_admin";

/**
 * Le choix de persona du développement, sans Keycloak : l'API ne l'offre que dans les builds dev et test
 * (DevPersonaController ; 404 en production, et rien ne s'affiche). Chaque persona est une personne en base, avec
 * son Rôle et son Secteur : tout ce que ce Rôle peut faire devient faisable, sans se connecter.
 */
export const DevPersonaSwitcher: React.FC = () => {
  const { preview } = useAuth();
  const queryClient = useQueryClient();
  const { data: sectors } = useSectors();
  const [error, setError] = useState(false);
  const offered = useQuery({
    queryKey: ["dev-persona"],
    queryFn: async () => ({ persona: (await apiFetch<Persona | undefined>("/api/dev/persona")) ?? null }),
    staleTime: Infinity,
    retry: false,
  });
  if (preview || !offered.isSuccess) return null;

  const current = offered.data.persona;
  const openSectors = (sectors ?? []).filter((s) => !s.closed);
  const choose = async (role: string, sectorId?: string | null) => {
    try {
      setError(false);
      if (!role) await apiFetch("/api/dev/persona", { method: "DELETE" });
      else
        await apiFetch("/api/dev/persona", {
          method: "PUT",
          body: JSON.stringify({ role, sectorId: hasSecteur(role as RoleId) ? sectorId || undefined : undefined }),
        });
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
        <select value={current?.role ?? ""} onChange={(e) => choose(e.target.value, current?.sectorId)}>
          <option value="">Visiteur</option>
          {PERSONAS.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </select>
      </label>
      {current && hasSecteur(current.role) && openSectors.length > 0 && (
        <label>
          <span className="sr-only">Secteur du persona</span>
          <select value={current.sectorId ?? ""} onChange={(e) => choose(current.role, e.target.value)}>
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
