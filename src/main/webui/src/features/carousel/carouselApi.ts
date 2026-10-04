import { CarouselItem, CarouselItemInput } from "./types";
import { apiFetch } from "../../lib/http";

const base = (id: string) => `/api/carousel/${encodeURIComponent(id)}`;

/**
 * Le carrousel d'accueil, sur CarouselController : les Visiteurs n'en reçoivent que les slides actifs, le
 * Bureau les reçoit tous, dans l'ordre, et les écrit, les réordonne ou les met de côté.
 */
export const fetchCarouselItems = () => apiFetch<CarouselItem[]>("/api/carousel");

/** Les slides des Secteurs que gère la personne (tous pour le Super admin) : l'écran Carrousel. */
export const fetchManagedCarouselItems = () => apiFetch<CarouselItem[]>("/api/carousel?managed=true");

/** Les slides affichés : même le Bureau ne voit pas sur l'accueil ceux mis de côté. */
export const fetchActiveCarouselItems = async () => (await fetchCarouselItems()).filter((item) => item.active);

export const createCarouselItem = (input: CarouselItemInput) =>
  apiFetch<CarouselItem>("/api/carousel", { method: "POST", body: JSON.stringify(input) });

export const updateCarouselItem = (id: string, patch: CarouselItemInput) =>
  apiFetch<CarouselItem>(base(id), { method: "PUT", body: JSON.stringify(patch) });

export const deleteCarouselItem = (id: string) => apiFetch<void>(base(id), { method: "DELETE" });

export const moveCarouselItem = (id: string, direction: "up" | "down") =>
  apiFetch<CarouselItem[]>(`${base(id)}/move/${direction}`, { method: "POST" });
