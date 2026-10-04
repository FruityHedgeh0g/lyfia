/**
 * Noms des Fonctionnalités : les leviers d'urgence du Super admin (ADR 0009), lus par useFeature et la carte
 * d'accès (auth/access.ts). Mêmes noms que FeatureEnum côté backend.
 */
export type FeatureName = "dons-en-ligne" | "inscription-evenements" | "galerie-photos";

/** Ce que chaque Fonctionnalité laisse faire, pour la page Fonctionnalités et les messages de suspension. */
export const FEATURE_LABELS: Record<FeatureName, string> = {
  "dons-en-ligne": "Dons en ligne",
  "inscription-evenements": "Inscription aux événements",
  "galerie-photos": "Galerie photos",
};

/** Reflète FeatureDto côté backend. */
export interface FeatureFlag {
  name: FeatureName;
  description: string;
  isActive: boolean;
}
