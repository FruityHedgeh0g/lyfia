/** Élément du carrousel d'accueil : reflète CarouselItemDto côté backend. */
export interface CarouselItem {
  id: string;
  title: string;
  caption: string;
  /** Référence vers features/medias ; vide = logo de l'association (image par défaut). */
  mediaId: string | null;
  /** Route interne optionnelle (ex : "/evenements" ou "/#benevolat"). */
  linkTo: string | null;
  /** Permet de désactiver temporairement un slide sans le supprimer. */
  active: boolean;
  order: number;
  /** Le Secteur dont le Bureau le gère ; null : tout le site (ADR 0004). */
  sectorId?: string | null;
}

/** `sectorId` n'est choisi que par le Super admin, à la création ; les autres écrivent pour leur Secteur. */
export type CarouselItemInput = Omit<CarouselItem, "id" | "order">;
