/** Qui voit une Photo de la Galerie : le Bureau (Brouillon), toute personne connectée (Inscrits), tout le monde (Publique). */
export type Visibilite = "brouillon" | "inscrits" | "publique";

export const VISIBILITE_LABELS: Record<Visibilite, string> = {
  brouillon: "Brouillon",
  inscrits: "Inscrits",
  publique: "Publique",
};

export const VISIBILITES = Object.keys(VISIBILITE_LABELS) as Visibilite[];

/** Les fichiers d'une photo (ADR 0014) : la vignette des grilles, la version d'affichage en grand, l'original. */
export type Variante = "vignette" | "affichage" | "original";

/** Reflète AlbumDto : les photos d'un Secteur pour une Année, et combien la personne en voit (ADR 0013). */
export interface Album {
  sectorId: string;
  sectorName: string;
  annee: number;
  count: number;
}

/** Reflète GalleryPhotoDto ; `visibilite` n'est donnée qu'au Bureau de son Secteur. */
export interface GalleryPhoto {
  photoId: string;
  mediaId: string;
  alt: string | null;
  sectorId: string;
  annee: number;
  event: { eventId: string; name: string; startDateTime: string } | null;
  groups: { groupId: string; name: string }[];
  visibilite: Visibilite | null;
  depositedAt: string;
  /** Pour le Bureau de son Secteur : où le site l'utilise ; elle reste alors Publique et n'est pas supprimée. */
  usedBy?: string[];
  /** Une Proposition de photo d'un Chef de groupe, qui attend le Bureau. */
  proposition?: boolean;
}

/** La plus grande photo gardée, réglée par le Super admin : le navigateur réduit au-delà (ADR 0014). */
export interface Limites {
  maxPx: number;
  maxMo: number;
}

/** Ce que le Bureau dit d'une photo, tout à la fois : sans Event, son Année est donnée. */
export interface PhotoChange {
  alt: string;
  eventId: string | null;
  annee: number | null;
  groupIds: string[];
}

/** Ce qu'un dépôt dit des photos du lot. */
export interface DepositMeta {
  sectorId?: string;
  eventId?: string;
  annee?: number;
  groupIds: string[];
  alt?: string;
}
