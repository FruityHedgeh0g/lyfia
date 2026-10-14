import React from "react";
import { useSectorMutations, useSectors } from "../../features/sectors/useSector";
import { SectorInput } from "../../features/sectors/sectorsApi";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";

const emptyDraft: SectorInput = { name: "", description: "" };

/**
 * Secteurs (Super admin) : ouvrir, renommer, fermer et rouvrir. Un Secteur
 * n'est jamais supprimé (ADR 0003) : fermé, il passe en lecture seule, ses
 * Groupes perdent leur Chef et ses Événements non terminés sont Annulés.
 */
export const SectorsAdminPage: React.FC = () => {
  const { data: sectors, isLoading } = useSectors();
  const { update, create, close, reopen } = useSectorMutations();
  const pending = close.isPending || reopen.isPending;
  const { confirm, dialog } = useConfirm();

  const closeSector = async (sectorId: string, name: string) => {
    const confirmed = await confirm({
      title: `Fermer ${name} ?`,
      message:
        "Le secteur passera en lecture seule : ses groupes perdront leur chef et ses événements non terminés seront annulés. Vous pourrez le rouvrir, mais les événements annulés le resteront.",
      confirmLabel: "Fermer le secteur",
    });
    if (confirmed) close.mutate(sectorId);
  };

  if (isLoading) return <Spinner label="Chargement des secteurs..." />;

  return (
    <>
      {dialog}
      <AdminCrudList
      title="Secteurs"
      hint="Un secteur n'est jamais supprimé : fermé, il reste en lecture seule et n'est plus visible que du Super admin."
      items={sectors ?? []}
      idOf={(s) => s.sectorId}
      display={(s) => ({ title: s.name, subtitle: [s.closed && "Fermé", `${s.groups.length} groupe(s)`].filter(Boolean).join(" · ") })}
      toDraft={(s): SectorInput => ({ name: s.name, description: s.description })}
      renderFields={(draft, setDraft, sector) => (
        <>
          <FormField
            label="Nom du secteur"
            value={draft.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
            disabled={sector?.closed}
            required
          />
          <FormField
            label="Description"
            multiline
            rows={3}
            value={draft.description}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
            disabled={sector?.closed}
          />
          {sector && (
            <Button
              type="button"
              variant="outline"
              label={sector.closed ? "Rouvrir le secteur" : "Fermer le secteur"}
              disabled={pending}
              onClick={() => (sector.closed ? reopen.mutate(sector.sectorId) : closeSector(sector.sectorId, sector.name))}
            />
          )}
        </>
      )}
      onUpdate={(sectorId, draft) => update.mutateAsync({ sectorId, ...draft })}
      create={{
        buttonLabel: "+ Nouveau secteur",
        submitLabel: "Créer le secteur",
        emptyDraft,
        onCreate: (draft) => create.mutateAsync(draft),
      }}
    />
    </>
  );
};

export default SectorsAdminPage;
