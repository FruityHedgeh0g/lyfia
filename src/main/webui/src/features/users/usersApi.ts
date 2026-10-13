import { BureauFunction, Member } from "./types";
import { RoleId } from "../../auth/roles";
import { apiFetch } from "../../lib/http";

/** Les noms d'une personne, que seul un Admin corrige (ADR 0007). */
export interface MemberInput {
  firstName: string;
  lastName: string;
}

/** UserDto (vue Basic) : le Secteur y est imbriqué. */
interface UserDto {
  userId: string;
  firstName: string;
  lastName: string;
  role: RoleId;
  bureauFunction?: BureauFunction | null;
  sector?: { sectorId: string; name: string } | null;
}

const toMember = (dto: UserDto): Member => ({
  userId: dto.userId,
  firstName: dto.firstName,
  lastName: dto.lastName,
  role: dto.role,
  sectorId: dto.sector?.sectorId ?? null,
  bureauFunction: dto.bureauFunction ?? null,
});

const path = (userId: string) => `/api/users/${encodeURIComponent(userId)}`;

/**
 * Les Inscrits, sur UserController : toutes les personnes pour le Super admin, celles de son Secteur et
 * les Bénévoles qui y ont roulé pour le Bureau et l'Admin (ADR 0004). L'API applique la chaîne de
 * promotion et donne le Secteur d'une personne promue.
 */
export async function fetchAllMembers(): Promise<Member[]> {
  return (await apiFetch<UserDto[]>("/api/users")).map(toMember);
}

/** Admin et Super admin seulement ; personne ne se renomme soi-même (ADR 0007). */
export async function updateMember(userId: string, names: MemberInput): Promise<Member> {
  return toMember(await apiFetch<UserDto>(path(userId), { method: "PATCH", body: JSON.stringify(names) }));
}

/** `sectorId` : le Secteur que nomme le Super admin (nouvel Admin, ou premier Secteur). */
export async function changeRole(userId: string, role: RoleId, sectorId?: string): Promise<Member> {
  return toMember(await apiFetch<UserDto>(`${path(userId)}/role`, { method: "PUT", body: JSON.stringify({ role, sectorId: sectorId ?? null }) }));
}

/**
 * Admin et Super admin, jamais sur soi : la Fonction au Bureau d'un Bureau ou d'un Admin de son Secteur. null retire
 * un Admin du Bureau ; un nouveau Président laisse le précédent Membre du Bureau.
 */
export async function setBureauFunction(userId: string, fn: BureauFunction | null): Promise<Member> {
  return toMember(await apiFetch<UserDto>(`${path(userId)}/bureau-function`, { method: "PUT", body: JSON.stringify({ function: fn }) }));
}
