import React, { useState } from "react";
import Badge from "../../components/atoms/Badge/Badge";
import Button from "../../components/atoms/Button/Button";
import Select from "../../components/atoms/Select/Select";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import { useAValider, useGalerieMutations } from "../../features/galerie/useGalerie";
import { photoUrl } from "../../features/galerie/galerieApi";
import { GalleryPhoto, Visibilite, VISIBILITE_LABELS, VISIBILITES } from "../../features/galerie/types";
import { photoCaption } from "../../components/organisms/PhotoGrid/PhotoGrid";
import { formatDate } from "../../lib/formatDate";
import styles from "./GalleryAdminPage.module.css";

/**
 * « À valider » : les Propositions des Chefs de groupe du Secteur. Le Bureau valide chacune en choisissant sa
 * Visibilité, ou la refuse, ce qui la supprime.
 */
export const ToValidate: React.FC<{ sectorId: string; onOpen: (photo: GalleryPhoto) => void }> = ({ sectorId, onOpen }) => {
  const { data: propositions } = useAValider(sectorId);
  const { validate, remove } = useGalerieMutations();
  const { confirm, dialog } = useConfirm();
  const [chosen, setChosen] = useState<Record<string, Visibilite>>({});
  const pending = validate.isPending || remove.isPending;

  if (!propositions || propositions.length === 0) return dialog;

  return (
    <section className={styles.toValidate} aria-label="Photos à valider">
      {dialog}
      <h3 className={styles.toValidateTitle}>
        À valider <Badge label={String(propositions.length)} tone="accent" />
      </h3>
      <ul className={styles.proposals}>
        {propositions.map((photo) => {
          const visibilite = chosen[photo.photoId] ?? "publique";
          return (
            <li key={photo.photoId} className={styles.proposal}>
              <button type="button" className={styles.proposalThumb} onClick={() => onOpen(photo)} aria-label={`Agrandir : ${photoCaption(photo)}`}>
                <img src={photoUrl(photo.mediaId, "vignette")} alt={photoCaption(photo)} />
              </button>
              <span className={styles.proposalInfo}>
                <strong>{[photo.event?.name, ...photo.groups.map((g) => g.name)].filter(Boolean).join(" · ")}</strong>
                <span className={styles.muted}>Proposée le {formatDate(photo.depositedAt)}</span>
              </span>
              <Select
                label="Visibilité"
                value={visibilite}
                onChange={(v) => setChosen({ ...chosen, [photo.photoId]: v as Visibilite })}
                options={VISIBILITES.map((v) => ({ value: v, label: VISIBILITE_LABELS[v] }))}
              />
              <Button type="button" label="Valider" disabled={pending} onClick={() => validate.mutate({ photoId: photo.photoId, visibilite })} />
              <Button
                type="button"
                label="Refuser"
                variant="outline"
                disabled={pending}
                onClick={async () => {
                  const confirmed = await confirm({
                    title: "Refuser la photo ?",
                    message: "La photo proposée est supprimée.",
                    confirmLabel: "Refuser",
                  });
                  if (confirmed) remove.mutate(photo.photoId);
                }}
              />
            </li>
          );
        })}
      </ul>
    </section>
  );
};

export default ToValidate;
