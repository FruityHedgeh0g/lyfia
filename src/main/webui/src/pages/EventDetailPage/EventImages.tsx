import React, { useState } from "react";
import { Link } from "react-router-dom";
import PhotoGrid from "../../components/organisms/PhotoGrid/PhotoGrid";
import Lightbox from "../../components/organisms/Lightbox/Lightbox";
import { useAlbum } from "../../features/galerie/useGalerie";
import { useFeature } from "../../features/featureFlags/useFeatureFlags";
import { ACCESS } from "../../auth/access";
import { Event } from "../../features/events/types";
import styles from "./EventDetailPage.module.css";

/** Au plus autant de photos dans le bandeau ; les autres sont dans l'Album. */
const SHOWN = 8;

/** Le lien vers l'Album d'un Event dans la Galerie, resserré sur lui. */
export const albumLink = (event: Pick<Event, "eventId" | "sectorId" | "startDateTime">) =>
  `${ACCESS.gallery.path}?album=${encodeURIComponent(`${event.sectorId}:${new Date(event.startDateTime).getFullYear()}`)}&evenement=${encodeURIComponent(event.eventId)}`;

/**
 * « En images » : les photos de l'Event que la personne a le droit de voir (Publiques, et réservées aux Inscrits une
 * fois connectée), avec un lien vers son Album. Rien tant qu'il n'y en a pas, ni quand Galerie photos est coupée.
 */
export const EventImages: React.FC<{ event: Event }> = ({ event }) => {
  const galerieOn = useFeature("galerie-photos", event.sectorId);
  const year = new Date(event.startDateTime).getFullYear();
  const { data: photos } = useAlbum(galerieOn ? event.sectorId : undefined, year, { eventId: event.eventId });
  const [open, setOpen] = useState<number | null>(null);

  if (!galerieOn || !photos || photos.length === 0) return null;
  const shown = photos.slice(0, SHOWN);

  return (
    <section className={styles.images} aria-labelledby="en-images">
      <h2 id="en-images" className={styles.imagesTitle}>
        En images
      </h2>
      <PhotoGrid photos={shown} onOpen={setOpen} />
      <Lightbox photos={shown} index={open} onIndex={setOpen} />
      <Link className={styles.albumLink} to={albumLink(event)}>
        {photos.length > SHOWN ? `Voir les ${photos.length} photos de l'album` : "Voir l'album"}
      </Link>
    </section>
  );
};

export default EventImages;
