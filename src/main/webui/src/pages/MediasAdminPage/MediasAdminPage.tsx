import React from "react";
import { useMediaMutations, useMedias } from "../../features/medias/useMedias";
import { ACCEPTED_IMAGE_TYPES } from "../../features/medias/mediasApi";
import { Media } from "../../features/medias/types";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Spinner from "../../components/atoms/Spinner/Spinner";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import styles from "../ConfigurationPage/ConfigurationPage.module.css";

interface MediaDraft {
  alt: string;
  file: File | null;
}

const emptyDraft: MediaDraft = { alt: "", file: null };

const sizeLabel = (bytes: number) => (bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} Ko` : `${(bytes / 1024 / 1024).toFixed(1)} Mo`);

/**
 * Médiathèque (Bureau) : les images des actualités, du carrousel, de la galerie et du logo. Une image
 * ajoutée ne change plus ; on décrit ce qu'elle montre pour les lecteurs d'écran.
 */
export const MediasAdminPage: React.FC = () => {
  const { data: medias, isLoading } = useMedias();
  const { upload, describe } = useMediaMutations();
  const depositOn = useFeature("depot-medias");
  const depositOff = useFeatureOff("depot-medias");

  if (isLoading) return <Spinner label="Chargement de la médiathèque..." />;

  return (
    <>
    {depositOff && <FeatureNotice feature="depot-medias" message="Le dépôt de nouvelles images est temporairement suspendu." />}
    <AdminCrudList<Media, MediaDraft>
      title="Médiathèque"
      hint="Images JPEG, PNG, WebP ou GIF, de 8 Mo au plus."
      items={medias ?? []}
      idOf={(m) => m.mediaId}
      display={(m) => ({
        title: m.alt,
        subtitle: `${m.originalFilename} · ${sizeLabel(m.fileSize)}`,
        leading: <img className={styles.thumb} src={m.url} alt="" />,
      })}
      toDraft={(m) => ({ alt: m.alt, file: null })}
      renderFields={(draft, setDraft, media) => (
        <>
          {!media && (
            <FormField
              label="Image"
              type="file"
              accept={ACCEPTED_IMAGE_TYPES.join(",")}
              onChange={(e) => setDraft({ ...draft, file: (e.target as HTMLInputElement).files?.[0] ?? null })}
              required
            />
          )}
          <FormField label="Description" value={draft.alt} onChange={(e) => setDraft({ ...draft, alt: e.target.value })} required />
        </>
      )}
      onUpdate={(mediaId, draft) => describe.mutateAsync({ mediaId, alt: draft.alt })}
      create={
        depositOn
          ? {
              buttonLabel: "+ Ajouter une image",
              submitLabel: "Ajouter",
              emptyDraft,
              onCreate: (draft) =>
                draft.file ? upload.mutateAsync({ file: draft.file, alt: draft.alt }) : Promise.reject(new Error("Choisissez une image.")),
            }
          : undefined
      }
    />
    </>
  );
};

export default MediasAdminPage;
