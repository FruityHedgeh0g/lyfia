import React, { useState } from "react";
import { useAllMembers, useChangeRole, useSetBureauFunction, useUpdateCoordonnees, useUpdateMember } from "../../features/users/useMembers";
import { MemberInput } from "../../features/users/usersApi";
import { BUREAU_FUNCTION_LABELS, BureauFunction, Member } from "../../features/users/types";
import { useAuth } from "../../auth/AuthContext";
import { useSectors } from "../../features/sectors/useSector";
import { assignableRoles, ROLE_LABELS, RoleId, roleAtLeast } from "../../auth/roles";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";
import SearchBar from "../../components/molecules/SearchBar/SearchBar";
import { matchesSearch } from "../../lib/textSearch";
import { formatDate } from "../../lib/formatDate";
import styles from "./MembersAdminPage.module.css";

/**
 * `sectorId` : le Secteur que nomme le Super admin (nouvel Admin, ou premier Secteur).
 * `bureauFunction` : la Fonction au Bureau, "" pour un Admin qui n'y siège pas.
 * `phone` et `birthDate` : tenus par le Bureau pour un Bénévole sans compte seulement (ADR 0012).
 */
type MemberDraft = MemberInput & { role: RoleId; bureauFunction: BureauFunction | ""; sectorId: string; phone: string; birthDate: string };

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
  const updateCoordonnees = useUpdateCoordonnees();
  const { data: sectors } = useSectors();
  const sectorName = (sectorId?: string | null) => sectors?.find((s) => s.sectorId === sectorId)?.name;
  const [search, setSearch] = useState("");

  /** Rôle, Bureau et Secteur ; les coordonnées sont dans la fiche dépliée. */
  const subtitle = (member: Member) =>
    [
      ROLE_LABELS[member.role],
      member.withoutAccount && "Sans compte",
      member.role === "admin" && member.bureauFunction && "Bureau",
      member.bureauFunction === "president" && BUREAU_FUNCTION_LABELS.president,
      sectorName(member.sectorId),
    ]
      .filter(Boolean)
      .join(" · ");

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

  const save = async (userId: string, { role, bureauFunction, sectorId, phone, birthDate, ...names }: MemberDraft) => {
    const currentMember = members?.find((m) => m.userId === userId);
    // Un Bénévole sans compte n'a pas Mon profil : le Bureau tient ses coordonnées (ADR 0012)
    if (currentMember?.withoutAccount && (phone !== (currentMember.phone ?? "") || birthDate !== (currentMember.birthDate ?? "")))
      await updateCoordonnees.mutateAsync({ userId, phone, birthDate: birthDate || null });
    const renamed = names.firstName !== currentMember?.firstName || names.lastName !== currentMember?.lastName;
    if (renamed && renames(userId)) await updateMember.mutateAsync({ userId, ...names });
    let saved: Member | undefined = currentMember;
    // Seul le Super admin nomme un Secteur : le Secteur actuel n'est jamais renvoyé avec le Rôle
    const namedSector = currentMember && namesSector(currentMember, { role, bureauFunction, sectorId, phone, birthDate, ...names }) ? sectorId : "";
    if (role !== currentMember?.role) saved = await changeRole.mutateAsync({ userId, role, sectorId: namedSector || undefined });
    // Le changement de Rôle a déjà réglé la Fonction qu'il laisse ; seul ce qui en diffère est demandé
    if (setsBureauFunction(userId, role) && (bureauFunction || null) !== (saved?.bureauFunction ?? null))
      await setBureauFunction.mutateAsync({ userId, bureauFunction: bureauFunction || null });
  };

  if (isLoading) return <Spinner label="Chargement des inscrits..." />;

  // La recherche porte sur le texte de la ligne et les coordonnées, sans appel au serveur
  const shown = (members ?? []).filter((m) =>
    matchesSearch(`${m.firstName} ${m.lastName} ${subtitle(m)} ${m.phone ?? ""} ${m.email ?? ""}`, search)
  );

  return (
    <div className={styles.page}>
      {(members?.length ?? 0) > 0 && (
        <SearchBar
          label="Rechercher un inscrit"
          placeholder="Nom, rôle, secteur, téléphone, e-mail…"
          value={search}
          onChange={setSearch}
          count={`${shown.length} résultat(s)`}
        />
      )}
      {search.trim() !== "" && shown.length === 0 ? (
        <p className={styles.empty}>Aucun résultat pour « {search.trim()} ».</p>
      ) : (
        <AdminCrudList
          items={shown}
          idOf={(m) => m.userId}
          display={(member) => ({ title: `${member.firstName} ${member.lastName}`, subtitle: subtitle(member) })}
          toDraft={(m): MemberDraft => ({
            firstName: m.firstName,
            lastName: m.lastName,
            role: m.role,
            bureauFunction: m.bureauFunction ?? "",
            sectorId: m.sectorId ?? "",
            phone: m.phone ?? "",
            birthDate: m.birthDate ?? "",
          })}
          renderFields={(draft, setDraft, member) => {
            const roles = member ? rolesFor(member.userId, member.role) : [];
            return (
              <section className={styles.columns}>
                <div className={styles.column}>
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
                </div>
                {member && (
                  <div className={styles.column}>
                    {member.withoutAccount ? (
                      <>
                        <FormField
                          label="Téléphone"
                          type="tel"
                          value={draft.phone}
                          onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
                          placeholder="Non renseigné"
                        />
                        <FormField label="E-mail" value="" placeholder="Sans compte" readOnly />
                        <FormField
                          label="Date de naissance"
                          type="date"
                          value={draft.birthDate}
                          onChange={(e) => setDraft({ ...draft, birthDate: e.target.value })}
                        />
                      </>
                    ) : (
                      <>
                        <FormField label="Téléphone" value={member.phone ?? ""} placeholder="Non renseigné" readOnly />
                        <FormField label="E-mail" value={member.email ?? ""} placeholder="Non renseigné" readOnly />
                        <FormField
                          label="Date de naissance"
                          value={member.birthDate ? formatDate(member.birthDate) : ""}
                          placeholder="Non renseigné"
                          readOnly
                        />
                      </>
                    )}
                  </div>
                )}
              </section>
            );
          }}
          onUpdate={save}
        />
      )}
    </div>
  );
};

export default MembersAdminPage;
