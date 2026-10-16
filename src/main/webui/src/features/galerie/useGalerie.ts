import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../queryKeys";
import {
  changePhoto,
  deletePhoto,
  depositPhoto,
  fetchAlbum,
  fetchAlbums,
  fetchAValider,
  fetchLimites,
  fetchMyPropositions,
  proposePhoto,
  setVisibilite,
  validatePhoto,
  withdrawProposition,
} from "./galerieApi";
import { DepositMeta, PhotoChange, Visibilite } from "./types";
import { PreparedPhoto } from "./resize";

export function useAlbums() {
  return useQuery({ queryKey: queryKeys.galerie.albums, queryFn: fetchAlbums });
}

/** Un Album, resserré sur un Event ou un Groupe ; rien tant que l'Album n'est pas choisi. */
export function useAlbum(sectorId: string | undefined, annee: number | undefined, filters: { eventId?: string; groupId?: string } = {}) {
  return useQuery({
    queryKey: queryKeys.galerie.album(sectorId, annee, filters.eventId, filters.groupId),
    queryFn: () => fetchAlbum(sectorId!, annee!, filters),
    enabled: Boolean(sectorId && annee),
  });
}

export function useLimites(enabled = true) {
  return useQuery({ queryKey: queryKeys.galerie.limites, queryFn: fetchLimites, enabled });
}

/** Déposer, décrire, rendre visible, supprimer : réservé au Bureau du Secteur de la photo. */
export function useGalerieMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.galerie.all });
  /** Une photo d'un lot : on rafraîchit une fois le lot envoyé (`invalidate`). */
  const deposit = useMutation({ mutationFn: (input: { photo: PreparedPhoto; meta: DepositMeta }) => depositPhoto(input.photo, input.meta) });
  const change = useMutation({ mutationFn: (input: { photoId: string } & PhotoChange) => changePhoto(input.photoId, input), onSuccess: invalidate });
  const visibilite = useMutation({
    mutationFn: (input: { photoIds: string[]; visibilite: Visibilite }) => setVisibilite(input.photoIds, input.visibilite),
    onSuccess: invalidate,
  });
  const remove = useMutation({ mutationFn: deletePhoto, onSuccess: invalidate });
  const validate = useMutation({
    mutationFn: (input: { photoId: string; visibilite: Visibilite }) => validatePhoto(input.photoId, input.visibilite),
    onSuccess: invalidate,
  });
  return { deposit, change, visibilite, remove, validate, invalidate };
}

/** « À valider » : les Propositions des Chefs de groupe du Secteur. */
export function useAValider(sectorId: string | undefined) {
  return useQuery({ queryKey: queryKeys.galerie.aValider(sectorId), queryFn: () => fetchAValider(sectorId!), enabled: Boolean(sectorId) });
}

/** Les Propositions du Chef connecté, et ce qu'il en fait : proposer, retirer tant que ce n'est pas validé. */
export function usePropositions(enabled = true) {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.galerie.all });
  const mine = useQuery({ queryKey: queryKeys.galerie.propositions, queryFn: fetchMyPropositions, enabled });
  /** Une photo d'un lot : on rafraîchit une fois le lot envoyé (`invalidate`). */
  const propose = useMutation({
    mutationFn: (input: { photo: PreparedPhoto; eventId: string; alt?: string }) => proposePhoto(input.photo, input.eventId, input.alt),
  });
  const withdraw = useMutation({ mutationFn: withdrawProposition, onSuccess: invalidate });
  return { mine, propose, withdraw, invalidate };
}
