import { Limites } from "./types";

/** Le grand côté des variantes (ADR 0014) : la vue en grand, les grilles. */
export const AFFICHAGE_PX = 2000;
export const VIGNETTE_PX = 400;

const ACCEPTED = ["image/jpeg", "image/png", "image/webp", "image/gif"];

/** Une photo prête à déposer : l'original (réduit s'il dépasse les limites) et ses deux variantes. */
export interface PreparedPhoto {
  name: string;
  original: Blob;
  affichage: Blob;
  vignette: Blob;
}

export class PhotoRefusedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PhotoRefusedError";
  }
}

/** L'image redessinée sur `longSide` px au plus, en JPEG sur fond blanc (une transparence n'a pas lieu d'être ici). */
async function scaled(image: ImageBitmap, longSide: number, quality: number): Promise<Blob> {
  const ratio = Math.min(1, longSide / Math.max(image.width, image.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(image.width * ratio));
  canvas.height = Math.max(1, Math.round(image.height * ratio));
  const context = canvas.getContext("2d");
  if (!context) throw new PhotoRefusedError("Votre navigateur ne sait pas préparer cette photo.");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.imageSmoothingQuality = "high";
  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new PhotoRefusedError("La photo n'a pas pu être préparée."))), "image/jpeg", quality)
  );
}

/**
 * Prépare une photo avant l'envoi (ADR 0014) : le serveur ne décode aucune image, c'est le navigateur qui fait la
 * vignette et la version d'affichage, et qui réduit l'original au-delà de la taille permise par le Super admin.
 */
export async function preparePhoto(file: File, limites: Limites): Promise<PreparedPhoto> {
  if (!ACCEPTED.includes(file.type)) throw new PhotoRefusedError(`${file.name} : seules les images JPEG, PNG, WebP et GIF sont acceptées.`);
  const maxBytes = limites.maxMo * 1024 * 1024;
  const image = await createImageBitmap(file);
  try {
    const tooLarge = Math.max(image.width, image.height) > limites.maxPx;
    const original = tooLarge || file.size > maxBytes ? await scaled(image, limites.maxPx, 0.9) : file;
    if (original.size > maxBytes) throw new PhotoRefusedError(`${file.name} : la photo dépasse ${limites.maxMo} Mo même réduite.`);
    return {
      name: file.name,
      original,
      affichage: await scaled(image, AFFICHAGE_PX, 0.85),
      vignette: await scaled(image, VIGNETTE_PX, 0.8),
    };
  } finally {
    image.close();
  }
}
