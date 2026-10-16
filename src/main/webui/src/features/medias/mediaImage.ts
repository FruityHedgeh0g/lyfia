import { Media } from "./types";
import { photoUrl } from "../galerie/galerieApi";
import { assetUrl } from "../../lib/assetUrl";

/** Image utilisée quand aucun média n'est choisi (ou qu'il n'existe plus) : le logo de l'association. */
export const DEFAULT_IMAGE_SRC = assetUrl("logo_asso_transparent.png");

/** Valeur de sélection représentant "aucun média" dans les listes de choix. */
export const DEFAULT_MEDIA_VALUE = "";

/**
 * L'image d'un Media : son fichier d'affichage, qu'il soit dans la Galerie ou non (ADR 0014) ; sa description si on
 * la connaît, sinon `fallbackAlt`.
 */
export function mediaImage(mediaId: string | null | undefined, medias: Media[] | undefined, fallbackAlt: string) {
  const media = mediaId ? medias?.find((m) => m.mediaId === mediaId) : undefined;
  return { src: mediaId ? photoUrl(mediaId, "affichage") : DEFAULT_IMAGE_SRC, alt: media?.alt ?? fallbackAlt, isDefault: !mediaId };
}

/** Options d'un sélecteur d'image : le logo par défaut puis la médiathèque. */
export function mediaOptions(medias: Media[] | undefined) {
  return [
    { value: DEFAULT_MEDIA_VALUE, label: "Logo de l'association (par défaut)" },
    ...(medias ?? []).map((m) => ({ value: m.mediaId, label: m.alt })),
  ];
}
