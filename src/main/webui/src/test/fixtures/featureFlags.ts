import { FeatureFlag } from "../../features/featureFlags/types";

export const mockFeatureFlags: FeatureFlag[] = [
  { name: "dons-en-ligne", description: "Afficher le module de don en ligne sur le site public.", isActive: false },
  { name: "inscription-evenements", description: "Permettre l'inscription en ligne aux événements.", isActive: true },
  { name: "galerie-photos", description: "Afficher la galerie photos publique.", isActive: true },
  { name: "actualites", description: "Montrer les actualités au public.", isActive: true },
  { name: "carrousel", description: "Montrer le carrousel de la page d'accueil.", isActive: true },
  { name: "depot-medias", description: "Permettre au Bureau de déposer des images dans la médiathèque.", isActive: true },
  { name: "demandes-fonctionnalite", description: "Permettre au Bureau de demander des fonctionnalités.", isActive: true },
  { name: "export-liste", description: "Permettre au Bureau de télécharger la liste des inscrits d'un événement.", isActive: true },
];
