import React from "react";
import { useGroupMutations, useGroups } from "../../features/groups/useGroups";
import { GroupInput } from "../../features/groups/groupsApi";
import { GROUP_KIND_LABELS, GroupKind } from "../../features/groups/types";
import { useSectors } from "../../features/sectors/useSector";
import { useAllMembers } from "../../features/users/useMembers";
import { canLeadGroupe } from "../../auth/roles";
import { useAuth } from "../../auth/AuthContext";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";

/** `chefId` vide : le Groupe n'a pas de chef. */
type GroupDraft = GroupInput & { chefId: string };

/**
 * Groupes : le Bureau et les Admins gèrent ceux de leur Secteur, dont le
 * Secteur d'un nouveau Groupe est verrouillé ; le Super admin gère tous les
 * Secteurs et choisit (ADR 0004). Un Groupe est mené par un Chef de son Secteur.
 */
export const GroupsAdminPage: React.FC = () => {
  const { user, hasAtLeastRole } = useAuth();
  const isSuperAdmin = hasAtLeastRole("super_admin");
  const ownSectorId = user?.sector?.sectorId ?? "";
  const { data: allGroups, isLoading } = useGroups();
  const groups = isSuperAdmin ? allGroups : allGroups?.filter((g) => g.sectorId === ownSectorId);
  const { data: sectors } = useSectors();
  const { data: members } = useAllMembers();
  const { create, update, affectation } = useGroupMutations();

  /** Seules les personnes au moins Chef de groupe (Bureau compris) peuvent mener un Groupe. */
  const chefOptions = (groupId: string, sectorId: string) => [
    { value: "", label: "Aucun chef" },
    ...(members ?? [])
      .filter((m) => canLeadGroupe(m.role) && m.sectorId === sectorId)
      .map((m) => {
        const led = allGroups?.find((g) => g.chef?.userId === m.userId && g.groupId !== groupId);
        return { value: m.userId, label: `${m.firstName} ${m.lastName}${led ? ` (mène ${led.name})` : ""}` };
      }),
  ];

  const sectorOptions = (sectors ?? []).map((s) => ({ value: s.sectorId, label: s.name }));
  const emptyDraft: GroupDraft = {
    name: "",
    description: "",
    area: "",
    sectorId: isSuperAdmin ? sectorOptions[0]?.value ?? "" : ownSectorId,
    kind: "motards",
    chefId: "",
  };

  const save = async (groupId: string, { chefId, ...input }: GroupDraft) => {
    await update.mutateAsync({ groupId, ...input });
    const current = groups?.find((g) => g.groupId === groupId)?.chef?.userId ?? "";
    if (chefId === current) return;
    const chef = members?.find((m) => m.userId === chefId);
    await affectation.mutateAsync({
      groupId,
      chef: chef ? { userId: chef.userId, firstName: chef.firstName, lastName: chef.lastName } : null,
    });
  };

  if (isLoading) return <Spinner label="Chargement des groupes..." />;

  return (
    <AdminCrudList
      title="Groupes"
      items={groups ?? []}
      idOf={(g) => g.groupId}
      display={(g) => {
        const chef = g.chef ? `Chef : ${g.chef.firstName} ${g.chef.lastName}` : "Sans chef";
        return { title: g.name, subtitle: [GROUP_KIND_LABELS[g.kind], g.area, chef].filter(Boolean).join(" · ") };
      }}
      toDraft={(g): GroupDraft => ({
        name: g.name,
        description: g.description,
        area: g.area,
        sectorId: g.sectorId,
        kind: g.kind,
        chefId: g.chef?.userId ?? "",
      })}
      renderFields={(draft, setDraft, group) => (
        <>
          <FormField label="Nom du groupe" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} required />
          <FormField
            label="Description"
            multiline
            rows={3}
            value={draft.description}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
          />
          <FormField label="Zone couverte" value={draft.area} onChange={(e) => setDraft({ ...draft, area: e.target.value })} />
          {/* Un Groupe ne change de sorte que tant que seul son Chef est avec lui à un Événement à venir */}
          <Select
            label="Sorte"
            value={draft.kind}
            onChange={(kind) => setDraft({ ...draft, kind: kind as GroupKind })}
            options={(Object.keys(GROUP_KIND_LABELS) as GroupKind[]).map((k) => ({ value: k, label: GROUP_KIND_LABELS[k] }))}
          />
          <Select
            label="Secteur"
            value={draft.sectorId}
            onChange={(sectorId) => setDraft({ ...draft, sectorId })}
            options={sectorOptions}
            disabled={!isSuperAdmin || Boolean(group)}
          />
          {group && (
            <Select
              label="Chef de groupe"
              value={draft.chefId}
              onChange={(chefId) => setDraft({ ...draft, chefId })}
              options={chefOptions(group.groupId, group.sectorId)}
            />
          )}
        </>
      )}
      onUpdate={save}
      create={{
        buttonLabel: "+ Nouveau groupe",
        submitLabel: "Créer le groupe",
        emptyDraft,
        onCreate: ({ chefId: _chefId, ...input }) => create.mutateAsync(input),
      }}
    />
  );
};

export default GroupsAdminPage;
