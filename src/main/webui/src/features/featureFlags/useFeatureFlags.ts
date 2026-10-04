import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchFeatureFlags, fetchJournal, JOURNAL_PAGE_SIZE, setFeatureFlagActive } from "./featureFlagsApi";
import { FeatureName } from "./types";
import { queryKeys } from "../queryKeys";

export function useFeatureFlags() {
  return useQuery({ queryKey: queryKeys.featureFlags.all, queryFn: fetchFeatureFlags });
}

/**
 * État des fonctionnalités ; `isActive` renvoie false tant que les flags ne sont pas chargés, puis true pour une
 * Fonctionnalité que l'API ne connaît pas : un levier jamais tiré (comme FeatureLevers côté backend).
 */
export function useFeatures() {
  const { data, isSuccess } = useFeatureFlags();
  return {
    ready: isSuccess,
    isActive: (name: FeatureName) => (data ? (data.find((f) => f.name === name)?.isActive ?? true) : false),
  };
}

export function useFeature(name: FeatureName): boolean {
  return useFeatures().isActive(name);
}

/** La Fonctionnalité est connue pour être désactivée : de quoi dire ce qui est suspendu sans le faire clignoter au chargement. */
export function useFeatureOff(name: FeatureName): boolean {
  const { ready, isActive } = useFeatures();
  return ready && !isActive(name);
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

/** Le Journal, page après page : la suivante existe tant qu'une page arrive pleine. */
export function useJournal() {
  return useInfiniteQuery({
    queryKey: queryKeys.featureFlags.journal,
    queryFn: ({ pageParam }) => fetchJournal(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, _pages, lastPageParam) => (lastPage.length === JOURNAL_PAGE_SIZE ? lastPageParam + 1 : undefined),
  });
}
