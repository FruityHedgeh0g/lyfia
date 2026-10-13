import { apiFetch, isUnauthenticated } from "../lib/http";
import { RoleId } from "./roles";
import { BureauFunction } from "../features/users/types";

/** Reflète NestedSectorDto : le Secteur d'une personne à partir de Membre (ADR 0004). */
export interface UserSector {
  sectorId: string;
  name: string;
}

/** La personne connectée, telle que GET /api/users/me la renvoie. */
export interface CurrentUser {
  userId: string;
  firstName: string;
  lastName: string;
  role: RoleId;
  /** Aucun pour un Bénévole (le vivier commun) ni pour le Super admin (au-dessus des Secteurs). */
  sector: UserSector | null;
  /** Nécessaire pour s'inscrire à un Événement. */
  phone?: string;
  /** Sa Fonction au Bureau, s'il y siège (ADR 0001). */
  bureauFunction?: BureauFunction | null;
}

/** Ce qu'une personne modifie d'elle-même dans Mon espace (PATCH /api/users/me) : son téléphone ; ses noms, seul un Admin les corrige (ADR 0007). */
export interface Profile {
  phone?: string;
}

interface UserDto {
  userId: string;
  firstName: string;
  lastName: string;
  role: RoleId;
  phone?: string | null;
  sector?: UserSector | null;
  bureauFunction?: BureauFunction | null;
}

const toCurrentUser = (dto: UserDto): CurrentUser => ({
  userId: dto.userId,
  firstName: dto.firstName,
  lastName: dto.lastName,
  role: dto.role,
  sector: dto.sector ?? null,
  phone: dto.phone ?? undefined,
  bureauFunction: dto.bureauFunction ?? null,
});

/** La personne connectée, ou null pour un Visiteur (sans session). */
export async function fetchMe(): Promise<CurrentUser | null> {
  try {
    return toCurrentUser(await apiFetch<UserDto>("/api/users/me"));
  } catch (error) {
    if (isUnauthenticated(error)) return null;
    throw error;
  }
}

export async function updateMe(profile: Profile): Promise<CurrentUser> {
  return toCurrentUser(await apiFetch<UserDto>("/api/users/me", { method: "PATCH", body: JSON.stringify(profile) }));
}

/**
 * Connexion : le navigateur quitte le site pour Keycloak, puis revient sur `redirect`.
 * `register` ouvre directement le formulaire d'inscription de Keycloak.
 */
export function loginUrl(redirect = "/", register = false): string {
  const params = new URLSearchParams({ redirect });
  if (register) params.set("prompt", "create");
  return `/api/auth/login?${params}`;
}

/** Ferme la session du site et celle de Keycloak, puis revient à l'accueil. */
export const LOGOUT_URL = "/api/auth/logout";
