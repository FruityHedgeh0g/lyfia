/** Chef de groupe tel que renvoyé par l'API (NestedUserDto). */
export interface GroupChef {
  userId: string;
  firstName: string;
  lastName: string;
}

/** Un Groupe de motards couvre une partie du Secteur sur la route ; un Groupe de service tient une tâche à l'Événement. */
export type GroupKind = "motards" | "service";

export const GROUP_KIND_LABELS: Record<GroupKind, string> = {
  motards: "Groupe de motards",
  service: "Groupe de service",
};

/** Reflète GroupDto côté backend (vue Basic). */
export interface Group {
  groupId: string;
  name: string;
  description: string;
  /** Partie du Secteur couverte par le Groupe. */
  area: string;
  sectorId: string;
  kind: GroupKind;
  /** Chef de groupe de l'Affectation en cours ; null si le Groupe n'a pas de chef. */
  chef?: GroupChef | null;
}
