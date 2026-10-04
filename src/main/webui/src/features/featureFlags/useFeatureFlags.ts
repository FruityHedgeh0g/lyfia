import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchFeatureFlags, fetchJournal, JOURNAL_PAGE_SIZE, setFeatureFlagActive, setFeatureFlagActiveInSector } from "./featureFlagsApi";
import { FeatureName } from "./types";
import { queryKeys } from "../queryKeys";

export function useFeatureFlags() {
  return useQuery({ queryKey: queryKeys.featureFlags.all, queryFn: fetchFeatureFlags });
}

/**
 * État des fonctionnalités ; `isActive` renvoie false tant que les flags ne sont pas chargés, puis true pour une
 * Fonctionnalité que l'API ne connaît pas : un levier jamais tiré (comme FeatureLevers côté backend). Avec un
 * Secteur, elle n'est active que si son levier pour tout le site et celui du Secteur le sont (ADR 0009).
 * Si les flags ne peuvent pas être lus, tout est montré : c'est l'API qui refuse ce qui est désactivé, et un échec
 * de lecture ne doit pas cacher la moitié du site.
 */
export function useFeatures() {
  const { data, isSuccess, isError } = useFeatureFlags();
  return {
    ready: isSuccess || isError,
    isActive: (name: FeatureName, sectorId?: string | null) => {
      if (!data) return isError;
      const flag = data.find((f) => f.name === name);
      if (!flag) return true;
      return flag.isActive && !(sectorId && flag.offSectors?.includes(sectorId));
    },
  };
}

export function useFeature(name: FeatureName, sectorId?: string | null): boolean {
  return useFeatures().isActive(name, sectorId);
}

/** La Fonctionnalité est connue pour être désactivée : de quoi dire ce qui est suspendu sans le faire clignoter au chargement. */
export function useFeatureOff(name: FeatureName, sectorId?: string | null): boolean {
  const { ready, isActive } = useFeatures();
  return ready && !isActive(name, sectorId);
}

export function useSetFeatureFlagActive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: FeatureName; isActive: boolean; reason?: string }) =>
      setFeatureFlagActive(input.name, input.isActive, input.reason),
    // Le site suit aussitôt : ce que la Fonctionnalité couvre peut avoir changé, et le Journal s'est allongé
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

export function useSetFeatureFlagActiveInSector() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: FeatureName; sectorId: string; isActive: boolean; reason?: string }) =>
      setFeatureFlagActiveInSector(input.name, input.sectorId, input.isActive, input.reason),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

/** Le Journal, page après page : la suivante existe tant qu'une page arrive pleine. */
export function useJournal() {
  return useInfiniteQuery({
    queryKey: queryKeys.featureFlags.journal,
    queryFn: ({ pageParam }) => fetchJournal(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, _pages, lastPageParam) => (lastPage.length === JOURNAL_PAGE_SIZE ? lastPageParam + 1 : undefined),
  });
}
