import { Media } from "./types";
import { apiFetch } from "../../lib/http";

interface MediaDto {
  mediaId: string;
  fileKey: string;
  originalFilename: string;
  mimeType?: string | null;
  contentType?: string | null;
  fileSize: number;
  alt?: string | null;
}

export const mediaUrl = (mediaId: string) => `/api/medias/${encodeURIComponent(mediaId)}/content`;

const toMedia = (dto: MediaDto): Media => ({
  mediaId: dto.mediaId,
  fileKey: dto.fileKey,
  originalFilename: dto.originalFilename,
  contentType: dto.mimeType ?? dto.contentType ?? "",
  fileSize: dto.fileSize,
  url: mediaUrl(dto.mediaId),
  alt: dto.alt || dto.originalFilename,
});

/** Images JPEG, PNG, WebP ou GIF de 8 Mo au plus (ADR 0008). */
export const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];
export const MAX_IMAGE_SIZE = 8 * 1024 * 1024;

/**
 * La médiathèque, sur MediaController : lue par tous (le site public affiche ses images), alimentée et
 * décrite par le Bureau. Les fichiers sont gardés en base et servis par l'API (ADR 0008).
 */
export async function fetchMedias(): Promise<Media[]> {
  return (await apiFetch<MediaDto[]>("/api/medias")).map(toMedia);
}

/** La galerie publique : les mêmes images, refusées tant que Galerie photos est désactivée (sauf au Bureau). */
export async function fetchGallery(): Promise<Media[]> {
  return (await apiFetch<MediaDto[]>("/api/medias/gallery")).map(toMedia);
}

export async function uploadMedia(file: File, alt: string): Promise<Media> {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) throw new Error("Seules les images JPEG, PNG, WebP et GIF sont acceptées.");
  if (file.size > MAX_IMAGE_SIZE) throw new Error("Une image pèse 8 Mo au plus.");
  const form = new FormData();
  form.append("file", file);
  form.append("alt", alt);
  return toMedia(await apiFetch<MediaDto>("/api/medias", { method: "POST", body: form }));
}

export async function describeMedia(mediaId: string, alt: string): Promise<Media> {
  return toMedia(await apiFetch<MediaDto>(`/api/medias/${encodeURIComponent(mediaId)}`, { method: "PATCH", body: JSON.stringify({ alt }) }));
}
