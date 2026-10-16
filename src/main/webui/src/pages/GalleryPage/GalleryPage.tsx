import React, { useMemo, useState } from "react";
import PageHero from "../../components/organisms/PageHero/PageHero";
import PhotoGrid from "../../components/organisms/PhotoGrid/PhotoGrid";
import Lightbox from "../../components/organisms/Lightbox/Lightbox";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { useAlbum, useAlbums } from "../../features/galerie/useGalerie";
import { Album, GalleryPhoto } from "../../features/galerie/types";
import { inSecteurChoice, useSecteurChoice } from "../../features/sectors/secteurChoice";
import styles from "./GalleryPage.module.css";

const albumKey = (album: Pick<Album, "sectorId" | "annee">) => `${album.sectorId}:${album.annee}`;

/** Les Events et les Groupes que montrent les photos d'un Album, pour le filtrer. */
export function albumFilters(photos: GalleryPhoto[]) {
  const events = new Map<string, { eventId: string; name: string; startDateTime: string }>();
  const groups = new Map<string, string>();
  photos.forEach((p) => {
    if (p.event) events.set(p.event.eventId, p.event);
    p.groups.forEach((g) => groups.set(g.groupId, g.name));
  });
  return {
    events: [...events.values()].sort((a, b) => b.startDateTime.localeCompare(a.startDateTime)),
    groups: [...groups.entries()].map(([groupId, name]) => ({ groupId, name })).sort((a, b) => a.name.localeCompare(b.name)),
  };
}

/**
 * La Galerie (ADR 0013) : un Album par Secteur et par Année, le plus récent d'abord, que l'on resserre sur un Event
 * ou un Groupe ; une photo s'ouvre en grand. On n'y voit que ce que la Visibilité de chaque photo permet.
 */
export const GalleryPage: React.FC = () => {
  const { data: albums, isLoading, isError } = useAlbums();
  const { sectorId: secteurChoice } = useSecteurChoice();
  const [chosen, setChosen] = useState("");
  const [eventId, setEventId] = useState("");
  const [groupId, setGroupId] = useState("");
  const [open, setOpen] = useState<number | null>(null);

  const shownAlbums = useMemo(
    () =>
      (albums ?? [])
        .filter((a) => inSecteurChoice(secteurChoice, a.sectorId))
        .sort((a, b) => b.annee - a.annee || a.sectorName.localeCompare(b.sectorName)),
    [albums, secteurChoice]
  );
  const album = shownAlbums.find((a) => albumKey(a) === chosen) ?? shownAlbums[0];
  const { data: photos, isLoading: photosLoading } = useAlbum(album?.sectorId, album?.annee);
  const { events, groups } = albumFilters(photos ?? []);
  const shown = (photos ?? [])
    .filter((p) => !eventId || p.event?.eventId === eventId)
    .filter((p) => !groupId || p.groups.some((g) => g.groupId === groupId));

  const choose = (key: string) => {
    setChosen(key);
    setEventId("");
    setGroupId("");
  };

  return (
    <>
      <PageHero eyebrow="En images" title="Galerie photos" lead="Revivez nos collectes, événements et actions solidaires en images." />
      <div className="container">
        {isLoading ? (
          <Spinner label="Chargement de la galerie..." />
        ) : isError ? (
          <p className={styles.error}>Impossible de charger la galerie pour le moment.</p>
        ) : !album ? (
          <p className={styles.empty}>Aucune photo publiée pour le moment.</p>
        ) : (
          <>
            <div className={styles.filters}>
              <Select
                label="Album"
                value={albumKey(album)}
                onChange={choose}
                options={shownAlbums.map((a) => ({ value: albumKey(a), label: `${a.sectorName} · ${a.annee} (${a.count} photo${a.count > 1 ? "s" : ""})` }))}
              />
              {events.length > 0 && (
                <Select
                  label="Événement"
                  value={eventId}
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
            {photosLoading ? (
              <Spinner label="Chargement des photos..." />
            ) : shown.length === 0 ? (
              <p className={styles.empty}>Aucune photo ici pour le moment.</p>
            ) : (
              <PhotoGrid photos={shown} onOpen={setOpen} featured />
            )}
            <Lightbox photos={shown} index={open} onIndex={setOpen} />
          </>
        )}
      </div>
    </>
  );
};

export default GalleryPage;
