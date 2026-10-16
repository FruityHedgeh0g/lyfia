import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changeRole, fetchAllMembers, MemberInput, setBureauFunction, updateCoordonnees, updateMember } from "./usersApi";
import { BureauFunction } from "./types";
import { RoleId } from "../../auth/roles";
import { queryKeys } from "../queryKeys";

export function useAllMembers() {
  return useQuery({ queryKey: queryKeys.members.list, queryFn: fetchAllMembers });
}

export function useUpdateMember() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string } & MemberInput) => updateMember(input.userId, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.members.all }),
  });
}

export function useUpdateCoordonnees() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; phone: string; birthDate: string | null }) =>
      updateCoordonnees(input.userId, { phone: input.phone, birthDate: input.birthDate }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.members.all }),
  });
}

export function useChangeRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; role: RoleId; sectorId?: string }) => changeRole(input.userId, input.role, input.sectorId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.members.all }),
  });
}

export function useSetBureauFunction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; bureauFunction: BureauFunction | null }) => setBureauFunction(input.userId, input.bureauFunction),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.members.all }),
  });
}
