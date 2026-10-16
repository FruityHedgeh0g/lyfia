import React, { useState } from "react";
import { useRoster } from "../../features/events/useRoster";
import { rosterExportUrl } from "../../features/events/registrationsApi";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import Button from "../../components/atoms/Button/Button";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import AddPersonDialog from "./AddPersonDialog";

interface EventRosterActionsProps {
  eventId: string;
  sectorId: string;
  /** false une fois l'Événement archivé : plus personne n'est ajouté. */
  editable: boolean;
  /** true tant que l'Événement prend des inscriptions (Ouvert ou Complet) : le Bureau peut y ajouter quelqu'un. */
  acceptsSignUps: boolean;
}

/**
 * Les actions de la liste des inscrits, en bas de la fiche de l'Événement à côté d'« Enregistrer » : ajouter une
 * personne (ADR 0012) et télécharger la liste.
 */
export const EventRosterActions: React.FC<EventRosterActionsProps> = ({ eventId, sectorId, editable, acceptsSignUps }) => {
  const [adding, setAdding] = useState(false);
  const { roster, addPerson } = useRoster(eventId);
  const inscriptionOn = useFeature("inscription-evenements", sectorId);
  const exportOn = useFeature("export-liste", sectorId);
  const exportOff = useFeatureOff("export-liste", sectorId);

  return (
    <>
      {editable && acceptsSignUps && inscriptionOn && roster.data && (
        <>
          <Button type="button" label="Ajouter une personne" variant="outline" onClick={() => setAdding(true)} />
          <AddPersonDialog
            isOpen={adding}
            onClose={() => {
              setAdding(false);
              addPerson.reset();
            }}
            journees={roster.data.journees.map((j) => j.journee)}
            onAdd={(addition) => addPerson.mutateAsync(addition)}
            pending={addPerson.isPending}
            error={addPerson.error}
          />
        </>
      )}
      {exportOn ? (
        <ButtonLink to={rosterExportUrl(eventId)} label="Télécharger la liste (.xlsx)" variant="outline" download />
      ) : (
        exportOff && <FeatureNotice feature="export-liste" message="Le téléchargement de la liste est temporairement suspendu." />
      )}
    </>
  );
};

export default EventRosterActions;
