import { Group, GroupChef, GroupKind } from "./types";
import { apiFetch } from "../../lib/http";

export interface GroupInput {
  name: string;
  description: string;
  area: string;
  sectorId: string;
  kind: GroupKind;
}

/** GroupDto (vue Basic) ; `chef` manque dans la réponse à une création. */
interface GroupDto {
  groupId: string;
  name: string;
  description?: string | null;
  area?: string | null;
  sectorId: string;
  kind?: GroupKind | null;
  chef?: GroupChef | null;
}

const toGroup = (dto: GroupDto): Group => ({
  groupId: dto.groupId,
  name: dto.name,
  description: dto.description ?? "",
  area: dto.area ?? "",
  sectorId: dto.sectorId,
  kind: dto.kind ?? "motards",
  chef: dto.chef ?? null,
});

const path = (groupId: string) => `/api/groups/${encodeURIComponent(groupId)}`;

/**
 * Les Groupes, sur GroupController. L'API ne montre les Groupes d'un Secteur fermé qu'au Super admin, et
 * ils ne changent plus. Le Bureau crée un Groupe dans son propre Secteur ; seul le Super admin le choisit.
 * Lire les Groupes ne demande pas d'être connecté.
 */
export async function fetchGroups(): Promise<Group[]> {
  return (await apiFetch<GroupDto[]>("/api/groups")).map(toGroup);
}

export async function createGroup(input: GroupInput): Promise<Group> {
  return toGroup(await apiFetch<GroupDto>("/api/groups", { method: "POST", body: JSON.stringify(input) }));
}

export async function updateGroup(groupId: string, patch: GroupInput): Promise<Group> {
  const { name, description, area, kind } = patch;
  return toGroup(await apiFetch<GroupDto>("/api/groups", { method: "PATCH", body: JSON.stringify({ groupId, name, description, area, kind }) }));
}

/** Affectation : le Chef quitte le Groupe qu'il menait éventuellement. */
export async function setChef(groupId: string, chef: Pick<GroupChef, "userId">): Promise<Group> {
  return toGroup(await apiFetch<GroupDto>(`${path(groupId)}/chef/${encodeURIComponent(chef.userId)}`, { method: "PUT" }));
}

export async function clearChef(groupId: string): Promise<Group> {
  return toGroup(await apiFetch<GroupDto>(`${path(groupId)}/chef`, { method: "DELETE" }));
}
