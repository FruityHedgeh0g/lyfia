import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { decideDemande, fetchMonGroupe, fetchRoster, placeInGroup, promote, remove, setGroupMaximum, takeOutOfGroup } from "./registrationsApi";
import { queryKeys } from "../queryKeys";
import { useAuth } from "../../auth/AuthContext";

/** Actions sur le Groupe d'une personne à un Événement : le Chef sur le Groupe qu'il mène, le Bureau sur tous (l'API en juge). */
export function useGroupActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });

  const decide = useMutation({
    mutationFn: (input: { eventId: string; personId: string; accept: boolean }) =>
      decideDemande(input.eventId, input.personId, input.accept),
    onSuccess: invalidate,
  });
  const takeOut = useMutation({
    mutationFn: (input: { eventId: string; personId: string }) => takeOutOfGroup(input.eventId, input.personId),
    onSuccess: invalidate,
  });

  return { decide, takeOut };
}

/** Participants et Liste d'attente d'un Événement, gérés par le Bureau. */
export function useRoster(eventId: string) {
  const queryClient = useQueryClient();
  const roster = useQuery({ queryKey: queryKeys.myRegistrations.roster(eventId), queryFn: () => fetchRoster(eventId) });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });

  const moveUp = useMutation({ mutationFn: (personId: string) => promote(eventId, personId), onSuccess: invalidate });
  const removePerson = useMutation({ mutationFn: (personId: string) => remove(eventId, personId), onSuccess: invalidate });
  const place = useMutation({
    mutationFn: (input: { personId: string; groupId: string }) => placeInGroup(eventId, input.personId, input.groupId),
    onSuccess: invalidate,
  });

  return { roster, moveUp, removePerson, place };
}

/** Maximum d'un Groupe à un Événement, enregistré avec la fiche de l'Événement. */
export function useGroupMaximum() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { eventId: string; groupId: string; maximum: number | null }) =>
      setGroupMaximum(input.eventId, input.groupId, input.maximum),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all }),
  });
}

/** Mon groupe : le Groupe mené par la personne connectée et, par Événement, ses membres et Demandes. */
export function useMonGroupe() {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...queryKeys.myRegistrations.all, "mon-groupe", user?.userId],
    queryFn: fetchMonGroupe,
    enabled: Boolean(user),
  });
}
