import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changePoste, pair, Poste, decideDemande, unpair, fetchMonGroupe, fetchRoster, placeInGroup, promote, remove, setGroupMaximum, takeOutOfGroup } from "./registrationsApi";
import { queryKeys } from "../queryKeys";
import { useAuth } from "../../auth/AuthContext";
import { Journee } from "../../lib/journees";

/** Actions sur le Groupe d'une personne à un Événement : le Chef sur le Groupe qu'il mène, le Bureau sur tous (l'API en juge). */
export function useGroupActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });

  const decide = useMutation({
    mutationFn: (input: { eventId: string; personId: string; accept: boolean }) =>
      decideDemande(input.eventId, input.personId, input.accept),
    onSuccess: invalidate,
  });
  /** `withPassager` : les passagers d'un conducteur sortent aussi, sinon leur binôme prend fin. */
  const takeOut = useMutation({
    mutationFn: (input: { eventId: string; personId: string; withPassager?: boolean }) =>
      takeOutOfGroup(input.eventId, input.personId, input.withPassager ?? true),
    onSuccess: invalidate,
  });
  /** Associer un passager à un conducteur de son Groupe pour une Journée ; sans conducteur, mettre fin au binôme. */
  const pairWith = useMutation({
    mutationFn: (input: { eventId: string; passagerId: string; journee: Journee; conducteurId: string | null }) =>
      input.conducteurId
        ? pair(input.eventId, input.passagerId, input.journee, input.conducteurId)
        : unpair(input.eventId, input.passagerId, input.journee),
    onSuccess: invalidate,
  });

  return { decide, takeOut, pairWith };
}

/** Participants et Liste d'attente d'un Événement pour une Journée (aucune : la première), gérés par le Bureau. */
export function useRoster(eventId: string, journee?: Journee) {
  const queryClient = useQueryClient();
  const roster = useQuery({
    queryKey: [...queryKeys.myRegistrations.roster(eventId), journee ?? null],
    queryFn: () => fetchRoster(eventId, journee),
    placeholderData: (previous) => previous,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.myRegistrations.all });
  const journees = journee ? [journee] : undefined;

  /** Faire monter pour la Journée affichée ; un binôme monte ensemble. */
  const moveUp = useMutation({ mutationFn: (personId: string) => promote(eventId, personId, journees), onSuccess: invalidate });
  /** Retirer de la Journée affichée, avec ou sans son passager. */
  const removePerson = useMutation({
    mutationFn: (input: { personId: string; withPassager?: boolean }) =>
      remove(eventId, input.personId, { journees, withPassager: input.withPassager }),
    onSuccess: invalidate,
  });
  const place = useMutation({
    mutationFn: (input: { personId: string; groupId: string; withPassager?: boolean }) =>
      placeInGroup(eventId, input.personId, input.groupId, input.withPassager ?? true),
    onSuccess: invalidate,
  });

  const changePosteOf = useMutation({
    mutationFn: (input: { personId: string; poste: Poste }) => changePoste(eventId, input.personId, input.poste),
    onSuccess: invalidate,
  });

  return { roster, moveUp, removePerson, place, changePoste: changePosteOf };
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
