import { Group } from "../../features/groups/types";

export const mockGroups: Group[] = [
  {
    groupId: "group-1",
    name: "Groupe Algrange Centre",
    description: "Les motards du centre d'Algrange.",
    area: "Centre-ville d'Algrange",
    sectorId: "sector-1",
    kind: "motards",
    chef: { userId: "user-1", firstName: "Marc", lastName: "Weber" },
  },
  {
    groupId: "group-2",
    name: "Groupe Thionville",
    description: "Les motards de l'agglomération de Thionville.",
    area: "Agglomération de Thionville",
    sectorId: "sector-2",
    kind: "motards",
    chef: { userId: "user-6", firstName: "Nathalie", lastName: "Roth" },
  },
];
