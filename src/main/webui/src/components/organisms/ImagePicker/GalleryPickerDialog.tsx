import React, { useMemo, useState } from "react";
import Modal from "../../molecules/Modal/Modal";
import Select from "../../atoms/Select/Select";
import Spinner from "../../atoms/Spinner/Spinner";
import { useAlbum, useAlbums } from "../../../features/galerie/useGalerie";
import { photoUrl } from "../../../features/galerie/galerieApi";
import { useSectors } from "../../../features/sectors/useSector";
import { photoCaption } from "../PhotoGrid/PhotoGrid";
import styles from "./ImagePicker.module.css";

interface GalleryPickerDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onPick: (mediaId: string) => void;
  /** Le Secteur dont on montre les photos ; aucun (le Super admin) : il le choisit. */
  sectorId?: string | null;
  /** Pour l'image d'un Event : son Album et ses photos d'abord. */
  event?: { eventId: string; startDateTime: string } | null;
}

/**
 * Choisir une image dans la Galerie : les seules photos Publiques (une bannière, le carrousel et un Event sont
 * publics), filtrées par Année, Event ou Groupe ; pour un Event, ses propres photos d'abord.
 */
export const GalleryPickerDialog: React.FC<GalleryPickerDialogProps> = ({ isOpen, onClose, onPick, sectorId, event }) => {
  const { data: sectors } = useSectors();
  const { data: albums } = useAlbums();
  const [chosenSector, setChosenSector] = useState("");
  const sector = sectorId || chosenSector || sectors?.[0]?.sectorId || "";
  const eventYear = event ? new Date(event.startDateTime).getFullYear() : undefined;
  const years = useMemo(
    () => [...new Set((albums ?? []).filter((a) => a.sectorId === sector).map((a) => a.annee))].sort((a, b) => b - a),
    [albums, sector]
  );
  const [chosenYear, setChosenYear] = useState<number | null>(null);
  const annee = chosenYear ?? (eventYear && years.includes(eventYear) ? eventYear : years[0]);
  const [eventId, setEventId] = useState<string | null>(null);
  const [groupId, setGroupId] = useState("");
  const { data: photos, isLoading } = useAlbum(isOpen ? sector || undefined : undefined, isOpen ? annee : undefined);

  const publiques = (photos ?? []).filter((p) => p.visibilite === "publique");
  const events = [...new Map(publiques.filter((p) => p.event).map((p) => [p.event!.eventId, p.event!])).values()];
  const groups = [...new Map(publiques.flatMap((p) => p.groups).map((g) => [g.groupId, g])).values()];
  // Pour l'image d'un Event, ses photos d'abord, tant qu'on n'a pas choisi un autre filtre
  const shownEvent = eventId ?? (event && events.some((e) => e.eventId === event.eventId) ? event.eventId : "");
  const shown = publiques.filter((p) => !shownEvent || p.event?.eventId === shownEvent).filter((p) => !groupId || p.groups.some((g) => g.groupId === groupId));

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Choisir dans la Galerie">
      <div className={styles.pickerBody}>
        <div className={styles.pickerFilters}>
          {!sectorId && (
            <Select
              label="Secteur"
              value={sector}
              onChange={(s) => {
                setChosenSector(s);
                setChosenYear(null);
                setEventId(null);
              }}
              options={(sectors ?? []).map((s) => ({ value: s.sectorId, label: s.name }))}
            />
          )}
          {years.length > 0 && (
            <Select
              label="Année"
              value={String(annee)}
              onChange={(y) => {
                setChosenYear(Number(y));
                setEventId(null);
              }}
              options={years.map((y) => ({ value: String(y), label: String(y) }))}
            />
          )}
          {events.length > 0 && (
            <Select
              label="Événement"
              value={shownEvent}
              onChange={setEventId}
              options={[{ value: "", label: "Tous" }, ...events.map((e) => ({ value: e.eventId, label: e.name }))]}
            />
          )}
          {groups.length > 0 && (
            <Select
              label="Groupe"
              value={groupId}
              onChange={setGroupId}
              options={[{ value: "", label: "Tous" }, ...groups.map((g) => ({ value: g.groupId, label: g.name }))]}
            />
          )}
        </div>
        {isLoading ? (
          <Spinner label="Chargement des photos..." />
        ) : shown.length === 0 ? (
          <p className={styles.hint}>Aucune photo Publique ici : seules les photos Publiques de la Galerie peuvent servir sur le site public.</p>
        ) : (
          <ul className={styles.pickerGrid} aria-label="Photos Publiques">
            {shown.map((photo) => (
              <li key={photo.photoId}>
                <button
                  type="button"
                  className={styles.pickerItem}
                  onClick={() => {
                    onPick(photo.mediaId);
                    onClose();
                  }}
                  aria-label={`Choisir : ${photoCaption(photo)}`}
                >
                  <img src={photoUrl(photo.mediaId, "vignette")} alt={photoCaption(photo)} loading="lazy" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  );
};

export default GalleryPickerDialog;
