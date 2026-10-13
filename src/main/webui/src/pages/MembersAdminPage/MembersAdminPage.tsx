import React from "react";
import { useAllMembers, useChangeRole, useSetBureauFunction, useUpdateMember } from "../../features/users/useMembers";
import { MemberInput } from "../../features/users/usersApi";
import { BUREAU_FUNCTION_LABELS, BureauFunction, Member } from "../../features/users/types";
import { useAuth } from "../../auth/AuthContext";
import { useSectors } from "../../features/sectors/useSector";
import { assignableRoles, ROLE_LABELS, RoleId, roleAtLeast } from "../../auth/roles";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";

/**
 * `sectorId` : le Secteur que nomme le Super admin (nouvel Admin, ou premier Secteur).
 * `bureauFunction` : la Fonction au Bureau, "" pour un Admin qui n'y siège pas.
 */
type MemberDraft = MemberInput & { role: RoleId; bureauFunction: BureauFunction | ""; sectorId: string };

/** Le Rôle Bureau siège toujours au Bureau ; un Admin, s'il y est appelé (ADR 0001). */
const bureauFunctionsFor = (role: RoleId): (BureauFunction | "")[] =>
  role === "bureau" ? ["membre_du_bureau", "president"] : role === "admin" ? ["", "membre_du_bureau", "president"] : [];

/** La Fonction au Bureau qu'un changement de Rôle laisse : gardée entre Bureau et Admin, perdue en dessous. */
const bureauFunctionAfter = (role: RoleId, current: BureauFunction | ""): BureauFunction | "" =>
  role === "bureau" ? current || "membre_du_bureau" : role === "admin" ? current : "";

export const MembersAdminPage: React.FC = () => {
  const { data: members, isLoading } = useAllMembers();
  const { role: viewerRole, user } = useAuth();
  const updateMember = useUpdateMember();
  const changeRole = useChangeRole();
  const setBureauFunction = useSetBureauFunction();
  const { data: sectors } = useSectors();
  const sectorName = (sectorId?: string | null) => sectors?.find((s) => s.sectorId === sectorId)?.name;

  /** Le Super admin nomme le Secteur d'un nouvel Admin, ou d'une personne qui n'en a pas encore (ADR 0004). */
  const namesSector = (member: { role: RoleId; sectorId?: string | null }, draft: MemberDraft) =>
    viewerRole === "super_admin" &&
    draft.role !== member.role &&
    roleAtLeast(draft.role, "membre") &&
    (draft.role === "admin" || !member.sectorId);

  /** Rôles proposés pour une personne : aucun sur soi-même (chaîne de promotion). */
  const rolesFor = (userId: string, current: RoleId) => (userId === user?.userId ? [] : assignableRoles(viewerRole, current));

  /** Seul un Admin corrige les noms d'une personne, jamais les siens (ADR 0007). */
  const renames = (userId: string) => roleAtLeast(viewerRole, "admin") && userId !== user?.userId;

  /** Un Admin règle la Fonction au Bureau d'un Bureau ou d'un Admin, jamais la sienne. */
  const setsBureauFunction = (userId: string, role: RoleId) =>
    roleAtLeast(viewerRole, "admin") && userId !== user?.userId && bureauFunctionsFor(role).length > 0;

  const save = async (userId: string, { role, bureauFunction, sectorId, ...names }: MemberDraft) => {
    const currentMember = members?.find((m) => m.userId === userId);
    const renamed = names.firstName !== currentMember?.firstName || names.lastName !== currentMember?.lastName;
    if (renamed && renames(userId)) await updateMember.mutateAsync({ userId, ...names });
    let saved: Member | undefined = currentMember;
    // Seul le Super admin nomme un Secteur : le Secteur actuel n'est jamais renvoyé avec le Rôle
    const namedSector = currentMember && namesSector(currentMember, { role, bureauFunction, sectorId, ...names }) ? sectorId : "";
    if (role !== currentMember?.role) saved = await changeRole.mutateAsync({ userId, role, sectorId: namedSector || undefined });
    // Le changement de Rôle a déjà réglé la Fonction qu'il laisse ; seul ce qui en diffère est demandé
    if (setsBureauFunction(userId, role) && (bureauFunction || null) !== (saved?.bureauFunction ?? null))
      await setBureauFunction.mutateAsync({ userId, bureauFunction: bureauFunction || null });
  };

  if (isLoading) return <Spinner label="Chargement des inscrits..." />;

  return (
    <AdminCrudList
      items={members ?? []}
      idOf={(m) => m.userId}
      display={(member) => ({
        title: `${member.firstName} ${member.lastName}`,
        subtitle: [
          ROLE_LABELS[member.role],
          member.role === "admin" && member.bureauFunction && "Bureau",
          member.bureauFunction === "president" && BUREAU_FUNCTION_LABELS.president,
          sectorName(member.sectorId),
        ]
          .filter(Boolean)
          .join(" · "),
      })}
      toDraft={(m): MemberDraft => ({
        firstName: m.firstName,
        lastName: m.lastName,
        role: m.role,
        bureauFunction: m.bureauFunction ?? "",
        sectorId: m.sectorId ?? "",
      })}
      renderFields={(draft, setDraft, member) => {
        const roles = member ? rolesFor(member.userId, member.role) : [];
        return (
          <>
            <FormField
              label="Prénom"
              value={draft.firstName}
              onChange={(e) => setDraft({ ...draft, firstName: e.target.value })}
              disabled={!member || !renames(member.userId)}
              required
            />
            <FormField
              label="Nom"
              value={draft.lastName}
              onChange={(e) => setDraft({ ...draft, lastName: e.target.value })}
              disabled={!member || !renames(member.userId)}
              required
            />
            {roles.length > 0 && (
              <Select
                label="Rôle"
                value={draft.role}
                onChange={(role) =>
                  setDraft({ ...draft, role: role as RoleId, bureauFunction: bureauFunctionAfter(role as RoleId, draft.bureauFunction) })
                }
                options={roles.map((r) => ({ value: r, label: ROLE_LABELS[r] }))}
              />
            )}
            {member && namesSector(member, draft) && (
              <Select
                label="Secteur"
                value={draft.sectorId}
                onChange={(sectorId) => setDraft({ ...draft, sectorId })}
                options={[{ value: "", label: "Choisissez le secteur" }, ...(sectors ?? []).map((s) => ({ value: s.sectorId, label: s.name }))]}
              />
            )}
            {member && setsBureauFunction(member.userId, draft.role) && (
              <Select
                label="Bureau"
                value={draft.bureauFunction}
                onChange={(bureauFunction) => setDraft({ ...draft, bureauFunction: bureauFunction as BureauFunction | "" })}
                options={bureauFunctionsFor(draft.role).map((f) => ({ value: f, label: f ? BUREAU_FUNCTION_LABELS[f] : "—" }))}
              />
            )}
          </>
        );
      }}
      onUpdate={save}
    />
  );
};

export default MembersAdminPage;
