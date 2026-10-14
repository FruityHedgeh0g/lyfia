import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Poste, fetchMyRegistrations, requestGroup, signUp, withdraw } from "./registrationsApi";
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

/** Inscription comme conducteur ou passager, et désinscription de la personne connectée. */
export function useEventRegistration() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });

  const register = useMutation({
    /** `conducteur` : le prénom et le nom du conducteur avec qui vient un passager, rapproché par l'API. L'API lit le téléphone au profil. */
    mutationFn: (input: {
      eventId: string;
      poste: Poste;
      phone?: string;
      groupId?: string;
      conducteur?: string;
      journees?: Journee[];
      signataire?: string;
    }) => {
      if (!user) throw new Error("Connectez-vous pour vous inscrire.");
      return signUp(input.eventId, {
        poste: input.poste,
        groupId: input.groupId || undefined,
        conducteur: input.conducteur?.trim() || undefined,
        journees: input.journees,
        signataire: input.signataire,
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
