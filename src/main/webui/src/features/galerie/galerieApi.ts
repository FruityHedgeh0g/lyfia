import { apiFetch } from "../../lib/http";
import { Album, DepositMeta, GalleryPhoto, Limites, PhotoChange, Variante, Visibilite } from "./types";
import { PreparedPhoto } from "./resize";

/** Le fichier d'une photo, dans la variante voulue (GET /api/medias/{mediaId}/content). */
export const photoUrl = (mediaId: string, variante: Variante) =>
  `/api/medias/${encodeURIComponent(mediaId)}/content?variante=${variante}`;

/**
 * La Galerie, sur GalleryController : ses Albums et leurs photos, vus selon la Visibilité de chacune ; déposées,
 * décrites, rendues visibles et supprimées par le Bureau de leur Secteur (ADR 0013).
 */
export const fetchAlbums = () => apiFetch<Album[]>("/api/galerie/albums");

export function fetchAlbum(sectorId: string, annee: number, filters: { eventId?: string; groupId?: string } = {}) {
  const params = new URLSearchParams({ sectorId, annee: String(annee) });
  if (filters.eventId) params.set("eventId", filters.eventId);
  if (filters.groupId) params.set("groupId", filters.groupId);
  return apiFetch<GalleryPhoto[]>(`/api/galerie?${params}`);
}

export const fetchLimites = () => apiFetch<Limites>("/api/galerie/limites");

/** Une photo du lot, avec les variantes faites par le navigateur ; un lot est envoyé photo par photo. */
export function depositPhoto(photo: PreparedPhoto, meta: DepositMeta): Promise<GalleryPhoto> {
  const form = new FormData();
  form.append("original", photo.original, photo.name);
  form.append("affichage", photo.affichage, `affichage-${photo.name}`);
  form.append("vignette", photo.vignette, `vignette-${photo.name}`);
  if (meta.sectorId) form.append("sectorId", meta.sectorId);
  if (meta.eventId) form.append("eventId", meta.eventId);
  else if (meta.annee) form.append("annee", String(meta.annee));
  meta.groupIds.forEach((groupId) => form.append("groupId", groupId));
  if (meta.alt?.trim()) form.append("alt", meta.alt.trim());
  return apiFetch<GalleryPhoto>("/api/galerie", { method: "POST", body: form });
}

export const changePhoto = (photoId: string, change: PhotoChange) =>
  apiFetch<GalleryPhoto>(`/api/galerie/${encodeURIComponent(photoId)}`, { method: "PATCH", body: JSON.stringify(change) });

export const setVisibilite = (photoIds: string[], visibilite: Visibilite) =>
  apiFetch<GalleryPhoto[]>("/api/galerie/visibilite", { method: "PUT", body: JSON.stringify({ photoIds, visibilite }) });

export const deletePhoto = (photoId: string) => apiFetch<void>(`/api/galerie/${encodeURIComponent(photoId)}`, { method: "DELETE" });
