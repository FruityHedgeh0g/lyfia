import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchCurrentDecharge, uploadDecharge } from "./dechargeApi";

const key = ["decharge"] as const;

/** La décharge en vigueur, ou null tant qu'aucune n'est déposée. */
export function useCurrentDecharge(enabled = true) {
  return useQuery({ queryKey: key, queryFn: fetchCurrentDecharge, enabled });
}

/** Le Super admin dépose une nouvelle version. */
export function useUploadDecharge() {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn: uploadDecharge, onSuccess: () => queryClient.invalidateQueries({ queryKey: key }) });
}
