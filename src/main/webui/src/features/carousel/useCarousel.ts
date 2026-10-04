import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createCarouselItem,
  deleteCarouselItem,
  fetchActiveCarouselItems,
  fetchManagedCarouselItems,
  moveCarouselItem,
  updateCarouselItem,
} from "./carouselApi";
import { CarouselItemInput } from "./types";
import { queryKeys } from "../queryKeys";

/** Les éléments (actifs et inactifs) des Secteurs que gère la personne, pour l'écran d'administration. */
export function useCarouselItems() {
  return useQuery({ queryKey: queryKeys.carousel.all, queryFn: fetchManagedCarouselItems });
}

/** Éléments actifs uniquement, pour le carrousel public de l'accueil. */
export function useActiveCarouselItems() {
  return useQuery({ queryKey: queryKeys.carousel.active, queryFn: fetchActiveCarouselItems });
}

export function useCarouselMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.carousel.all });

  const create = useMutation({
    mutationFn: createCarouselItem,
    onSuccess: invalidate,
  });

  const update = useMutation({
    mutationFn: (input: { id: string } & CarouselItemInput) => updateCarouselItem(input.id, input),
    onSuccess: invalidate,
  });

  const remove = useMutation({
    mutationFn: deleteCarouselItem,
    onSuccess: invalidate,
  });

  const move = useMutation({
    mutationFn: (input: { id: string; direction: "up" | "down" }) => moveCarouselItem(input.id, input.direction),
    onSuccess: invalidate,
  });

  return { create, update, remove, move };
}
