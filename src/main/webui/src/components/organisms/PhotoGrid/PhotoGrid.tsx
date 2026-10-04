import React from "react";
import { useGallery } from "../../../features/medias/useMedias";
import Spinner from "../../atoms/Spinner/Spinner";
import styles from "./PhotoGrid.module.css";

export const PhotoGrid: React.FC = () => {
  const { data: medias, isLoading, isError } = useGallery();

  if (isLoading) return <Spinner label="Chargement de la galerie..." />;
  if (isError) return <p className={styles.error}>Impossible de charger la galerie pour le moment.</p>;
  if (!medias || medias.length === 0) return <p className={styles.empty}>Aucune photo publiée pour le moment.</p>;

  return (
    <div className={styles.grid}>
      {medias.map((media) => (
        <figure className={styles.item} key={media.mediaId}>
          <img className={styles.image} src={media.url} alt={media.alt} loading="lazy" />
          <figcaption className={styles.caption}>{media.alt}</figcaption>
        </figure>
      ))}
    </div>
  );
};

export default PhotoGrid;
