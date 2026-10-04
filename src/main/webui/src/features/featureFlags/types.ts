/**
 * Noms des Fonctionnalités : les leviers d'urgence du Super admin (ADR 0009), lus par useFeature et la carte
 * d'accès (auth/access.ts). Mêmes noms que FeatureEnum côté backend.
 */
export type FeatureName =
  | "dons-en-ligne"
  | "inscription-evenements"
  | "galerie-photos"
  | "actualites"
  | "carrousel"
  | "depot-medias"
  | "demandes-fonctionnalite"
  | "export-liste"
  | "inscription-site";

/** Ce que chaque Fonctionnalité laisse faire, pour la page Fonctionnalités et les messages de suspension. */
export const FEATURE_LABELS: Record<FeatureName, string> = {
  "dons-en-ligne": "Dons en ligne",
  "inscription-evenements": "Inscription aux événements",
  "galerie-photos": "Galerie photos",
  actualites: "Actualités",
  carrousel: "Carrousel",
  "depot-medias": "Dépôt de médias",
  "demandes-fonctionnalite": "Demandes de fonctionnalité",
  "export-liste": "Export de la liste",
  "inscription-site": "Inscription sur le site",
};

/** Reflète FeatureDto côté backend, avec le dernier changement de son levier pour tout le site (le Journal). */
export interface FeatureFlag {
  name: FeatureName;
  description: string;
  isActive: boolean;
  /** Elle a aussi un levier par Secteur (Inscription aux événements, Export de la liste). */
  perSecteur?: boolean;
  /** Les Secteurs où elle est désactivée, quel que soit son levier pour tout le site. */
  offSectors?: string[];
  /** Tentatives refusées depuis qu'elle a été désactivée. */
  refusedCount?: number;
  lastSwitchedBy?: string | null;
  lastSwitchedAt?: string | null;
  lastReason?: string | null;
}

/** Une entrée du Journal (FeatureSwitchEntryDto) ; `sectorId` null : tout le site. */
export interface JournalEntry {
  id: string;
  feature: FeatureName;
  sectorId: string | null;
  sectorName: string | null;
  isActive: boolean;
  reason: string | null;
  switchedBy: string | null;
  switchedAt: string;
}
