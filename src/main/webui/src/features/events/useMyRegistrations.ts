import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Poste, fetchMyRegistrations, fetchConducteurs, requestGroup, signUp, withdraw } from "./registrationsApi";
import { queryKeys } from "../queryKeys";
import { Journee } from "../../lib/journees";
import { useAuth } from "../../auth/AuthContext";

export function useMyRegistrations() {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...queryKeys.myRegistrations.all, user?.userId],
    queryFn: fetchMyRegistrations,
    enabled: Boolean(user),
  });
}

/** Les conducteurs inscrits à un Événement, parmi lesquels un passager choisit. */
export function useConducteurs(eventId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.myRegistrations.conducteurs(eventId),
    queryFn: () => fetchConducteurs(eventId ?? ""),
    enabled: Boolean(eventId),
  });
}

/** Inscription comme conducteur ou passager, et désinscription de la personne connectée. */
export function useEventRegistration() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });

  const register = useMutation({
    /** `conducteurId` : s'inscrire comme passager de ce conducteur (sans Demande de groupe). L'API lit le téléphone au profil. */
    mutationFn: (input: { eventId: string; poste: Poste; phone?: string; groupId?: string; conducteurId?: string; journees?: Journee[] }) => {
      if (!user) throw new Error("Connectez-vous pour vous inscrire.");
      return signUp(input.eventId, {
        poste: input.poste,
        groupId: input.groupId || undefined,
        conducteurId: input.conducteurId || undefined,
        journees: input.journees,
      });
    },
    onSuccess: invalidate,
  });

  /** Se retirer de Journées (aucune : toutes), avec ou sans son passager. */
  const unregister = useMutation({
    mutationFn: (input: { eventId: string; journees?: Journee[]; withPassager?: boolean }) =>
      withdraw(input.eventId, { journees: input.journees, withPassager: input.withPassager }),
    onSuccess: invalidate,
  });

  /** Nouvelle Demande de groupe, par exemple après un refus. */
  const askGroup = useMutation({
    mutationFn: (input: { eventId: string; groupId: string }) => requestGroup(input.eventId, input.groupId),
    onSuccess: invalidate,
  });

  return { register, unregister, askGroup };
}
