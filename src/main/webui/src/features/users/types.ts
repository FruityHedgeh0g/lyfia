import { RoleId } from "../../auth/roles";

/** Fonction au Bureau (ADR 0001) : celle de toute personne au Rôle Bureau, et d'un Admin appelé au Bureau. */
export type BureauFunction = "membre_du_bureau" | "president";

export const BUREAU_FUNCTION_LABELS: Record<BureauFunction, string> = {
  membre_du_bureau: "Membre du Bureau",
  president: "Président",
};

/** Une personne de la liste des Inscrits : reflète UserDto côté backend (vue Detailed). */
export interface Member {
  userId: string;
  firstName: string;
  lastName: string;
  role: RoleId;
  /** Secteur de la personne à partir de Membre (ADR 0004) ; aucun pour un Bénévole ou le Super admin. */
  sectorId?: string | null;
  /** Sa Fonction au Bureau, s'il y siège ; un seul Président par Secteur. */
  bureauFunction?: BureauFunction | null;
  /** Coordonnées saisies dans Mon profil ; l'e-mail est celui du compte. */
  phone?: string | null;
  email?: string | null;
  /** AAAA-MM-JJ. */
  birthDate?: string | null;
}
