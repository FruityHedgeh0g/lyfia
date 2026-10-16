import React, { useMemo, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { useIsSuperAdmin, useSectors } from "../../features/sectors/useSector";
import { useEvents } from "../../features/events/useEvents";
import { useGroups } from "../../features/groups/useGroups";
import { useAlbum, useAlbums, useGalerieMutations } from "../../features/galerie/useGalerie";
import { GalleryPhoto, Visibilite, VISIBILITE_LABELS, VISIBILITES } from "../../features/galerie/types";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import PhotoGrid from "../../components/organisms/PhotoGrid/PhotoGrid";
import Select from "../../components/atoms/Select/Select";
import Button from "../../components/atoms/Button/Button";
import Badge from "../../components/atoms/Badge/Badge";
import Spinner from "../../components/atoms/Spinner/Spinner";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import MediasAdminPage from "../MediasAdminPage/MediasAdminPage";
import { albumFilters } from "../GalleryPage/GalleryPage";
import DepositDialog from "./DepositDialog";
import PhotoEditDialog from "./PhotoEditDialog";
import styles from "./GalleryAdminPage.module.css";

const VISIBILITE_TONES: Record<Visibilite, "muted" | "primary" | "accent"> = { brouillon: "muted", inscrits: "primary", publique: "accent" };

/**
 * Les Albums d'un Secteur (ADR 0013) : le Bureau y dépose des photos en lot, les décrit, règle leur Visibilité une à
 * une ou pour une sélection, et les supprime. Le Super admin choisit le Secteur.
 */
const Albums: React.FC = () => {
  const { user } = useAuth();
  const superAdmin = useIsSuperAdmin();
  const { data: sectors } = useSectors();
  const { data: albums } = useAlbums();
  const { data: allEvents } = useEvents();
  const { data: allGroups } = useGroups();
  const { visibilite } = useGalerieMutations();
  const depositOn = useFeature("depot-medias");
  const depositOff = useFeatureOff("depot-medias");

  const [chosenSector, setChosenSector] = useState("");
  const sectorId = superAdmin ? chosenSector || sectors?.[0]?.sectorId || "" : user?.sector?.sectorId ?? "";
  const sector = sectors?.find((s) => s.sectorId === sectorId);
  const editable = !sector?.closed;

  const currentYear = new Date().getFullYear();
  const years = useMemo(() => {
    const known = new Set((albums ?? []).filter((a) => a.sectorId === sectorId).map((a) => a.annee));
    known.add(currentYear);
    return [...known].sort((a, b) => b - a);
  }, [albums, sectorId, currentYear]);
  const [chosenYear, setChosenYear] = useState<number | null>(null);
  const annee = chosenYear !== null && years.includes(chosenYear) ? chosenYear : years[0];

  const { data: photos, isLoading } = useAlbum(sectorId || undefined, annee);
  const [eventId, setEventId] = useState("");
  const [groupId, setGroupId] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [bulk, setBulk] = useState<Visibilite>("publique");
  const [depositing, setDepositing] = useState(false);
  const [editing, setEditing] = useState<GalleryPhoto | null>(null);

  const events = (allEvents ?? [])
    .filter((e) => e.sectorId === sectorId)
    .sort((a, b) => b.startDateTime.localeCompare(a.startDateTime));
  const groups = (allGroups ?? []).filter((g) => g.sectorId === sectorId);
  const filters = albumFilters(photos ?? []);
  const shown = (photos ?? [])
    .filter((p) => !eventId || p.event?.eventId === eventId)
    .filter((p) => !groupId || p.groups.some((g) => g.groupId === groupId));
  const shownSelected = selected.filter((id) => shown.some((p) => p.photoId === id));

  const toggle = (photoId: string, on: boolean) => setSelected(on ? [...selected, photoId] : selected.filter((id) => id !== photoId));
  const reset = () => {
    setEventId("");
    setGroupId("");
    setSelected([]);
  };

  if (!sectorId) return <p className={styles.muted}>Aucun Secteur à gérer.</p>;

  return (
    <div className={styles.albums}>
      {depositOff && <FeatureNotice feature="depot-medias" message="Le dépôt de nouvelles photos est temporairement suspendu." />}
      <div className={styles.toolbar}>
        {superAdmin && (
          <Select
            label="Secteur"
            value={sectorId}
            onChange={(id) => {
              setChosenSector(id);
              setChosenYear(null);
              reset();
            }}
            options={(sectors ?? []).map((s) => ({ value: s.sectorId, label: s.closed ? `${s.name} (fermé)` : s.name }))}
          />
        )}
        <Select
          label="Année"
          value={String(annee)}
          onChange={(y) => {
            setChosenYear(Number(y));
            reset();
          }}
          options={years.map((y) => ({ value: String(y), label: String(y) }))}
        />
        {filters.events.length > 0 && (
          <Select
            label="Événement"
            value={eventId}
            onChange={setEventId}
            options={[{ value: "", label: "Tous" }, ...filters.events.map((e) => ({ value: e.eventId, label: e.name }))]}
          />
        )}
        {filters.groups.length > 0 && (
          <Select
            label="Groupe"
            value={groupId}
            onChange={setGroupId}
            options={[{ value: "", label: "Tous" }, ...filters.groups.map((g) => ({ value: g.groupId, label: g.name }))]}
          />
        )}
        {editable && depositOn && <Button type="button" label="Déposer des photos" onClick={() => setDepositing(true)} />}
      </div>

      {editable && shown.length > 0 && (
        <div className={styles.selection}>
          <Button
            type="button"
            variant="outline"
            label={shownSelected.length === shown.length ? "Tout désélectionner" : "Tout sélectionner"}
            onClick={() => setSelected(shownSelected.length === shown.length ? [] : shown.map((p) => p.photoId))}
          />
          <span className={styles.muted}>{shownSelected.length} sélectionnée(s)</span>
          <Select label="Visibilité" value={bulk} onChange={(v) => setBulk(v as Visibilite)} options={VISIBILITES.map((v) => ({ value: v, label: VISIBILITE_LABELS[v] }))} />
          <Button
            type="button"
            label="Appliquer"
            disabled={shownSelected.length === 0 || visibilite.isPending}
            onClick={() => visibilite.mutate({ photoIds: shownSelected, visibilite: bulk }, { onSuccess: () => setSelected([]) })}
          />
        </div>
      )}
      {visibilite.error && <p className={styles.error}>{visibilite.error.message}</p>}

      {isLoading ? (
        <Spinner label="Chargement de l'album..." />
      ) : shown.length === 0 ? (
        <p className={styles.empty}>Aucune photo dans cet album.</p>
      ) : (
        <PhotoGrid
          photos={shown}
          onOpen={(index) => setEditing(shown[index])}
          overlay={(photo) => (
            <>
              {editable ? (
                <label className={styles.select}>
                  <input
                    type="checkbox"
                    checked={selected.includes(photo.photoId)}
                    onChange={(e) => toggle(photo.photoId, e.target.checked)}
                    aria-label={`Sélectionner ${photo.alt || photo.event?.name || "la photo"}`}
                  />
                </label>
              ) : (
                <span />
              )}
              {photo.visibilite && <Badge label={VISIBILITE_LABELS[photo.visibilite]} tone={VISIBILITE_TONES[photo.visibilite]} />}
            </>
          )}
        />
      )}

      <DepositDialog
        isOpen={depositing}
        onClose={() => setDepositing(false)}
        sectorId={sectorId}
        namesSector={superAdmin}
        events={events}
        groups={groups}
        onDeposited={(y) => {
          setChosenYear(y);
          reset();
        }}
      />
      <PhotoEditDialog photo={editing} onClose={() => setEditing(null)} events={events} groups={groups} editable={editable} />
    </div>
  );
};

type Tab = "albums" | "autres";

/** La Galerie en administration : ses Albums, et les autres images du site (bannières, carrousel, logo). */
export const GalleryAdminPage: React.FC = () => {
  const [tab, setTab] = useState<Tab>("albums");
  return (
    <div className={styles.page}>
      <div className={styles.tabs} role="tablist" aria-label="Galerie">
        {(
          [
            ["albums", "Albums"],
            ["autres", "Autres images"],
          ] as [Tab, string][]
        ).map(([id, label]) => (
          <button key={id} type="button" role="tab" aria-selected={tab === id} className={styles.tab} onClick={() => setTab(id)}>
            {label}
          </button>
        ))}
      </div>
      {tab === "albums" ? <Albums /> : <MediasAdminPage />}
    </div>
  );
};

export default GalleryAdminPage;
