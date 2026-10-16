import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { describeMedia, fetchMedias, uploadMedia } from "./mediasApi";
import { queryKeys } from "../queryKeys";

export function useMedias() {
  return useQuery({ queryKey: queryKeys.medias.all, queryFn: fetchMedias });
}

/** Ajouter une image à la médiathèque, ou décrire ce qu'elle montre : réservé au Bureau. */
export function useMediaMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.medias.all });
  const upload = useMutation({ mutationFn: (input: { file: File; alt: string }) => uploadMedia(input.file, input.alt), onSuccess: invalidate });
  const describe = useMutation({ mutationFn: (input: { mediaId: string; alt: string }) => describeMedia(input.mediaId, input.alt), onSuccess: invalidate });
  return { upload, describe };
}
