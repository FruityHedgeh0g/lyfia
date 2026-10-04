import React from "react";
import { useCarouselItems, useCarouselMutations } from "../../features/carousel/useCarousel";
import { CarouselItem, CarouselItemInput } from "../../features/carousel/types";
import { useMedias } from "../../features/medias/useMedias";
import { DEFAULT_MEDIA_VALUE, mediaImage, mediaOptions } from "../../features/medias/mediaImage";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import Badge from "../../components/atoms/Badge/Badge";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { useIsSuperAdmin, useSectors } from "../../features/sectors/useSector";
import styles from "./CarouselAdminPage.module.css";

interface Draft {
  title: string;
  caption: string;
  mediaId: string;
  linkTo: string;
  active: boolean;
  /** Choisi par le Super admin, à la création ; "" : tout le site. */
  sectorId: string;
}

const emptyDraft: Draft = { title: "", caption: "", mediaId: DEFAULT_MEDIA_VALUE, linkTo: "", active: true, sectorId: "" };

const toDraft = (item: CarouselItem): Draft => ({
  title: item.title,
  caption: item.caption,
  mediaId: item.mediaId ?? DEFAULT_MEDIA_VALUE,
  linkTo: item.linkTo ?? "",
  active: item.active,
  sectorId: item.sectorId ?? "",
});

function draftToInput(draft: Draft): CarouselItemInput {
  return {
    title: draft.title,
    caption: draft.caption,
    mediaId: draft.mediaId || null,
    linkTo: draft.linkTo.trim() || null,
    active: draft.active,
    sectorId: draft.sectorId || null,
  };
}

/**
 * Carrousel (Bureau) : les éléments de son Secteur (tous pour le Super admin), ordonnés entre eux. Un nouvel élément
 * est pour le Secteur de la personne ; le Super admin choisit le Secteur, ou tout le site (ADR 0004).
 */
export const CarouselAdminPage: React.FC = () => {
  const { data: items, isLoading } = useCarouselItems();
  const superAdmin = useIsSuperAdmin();
  const { data: sectors } = useSectors();
  const sectorOptions = [
    { value: "", label: "Tout le site" },
    ...(sectors ?? []).filter((s) => !s.closed).map((s) => ({ value: s.sectorId, label: s.name })),
  ];
  const { data: medias, isLoading: mediasLoading } = useMedias();
  const { create, update, remove, move } = useCarouselMutations();

  if (isLoading || mediasLoading) return <Spinner label="Chargement du carrousel..." />;

  const sortedItems = items ?? [];
  const options = mediaOptions(medias);

  return (
    <AdminCrudList
      title="Carrousel"
      hint="Éléments affichés dans le carrousel de la page d'accueil, dans l'ordre ci-dessous. Un élément inactif reste enregistré mais n'apparaît plus sur le site."
      items={sortedItems}
      idOf={(item) => item.id}
      display={(item, index) => ({
        title: item.title,
        subtitle: superAdmin
          ? [item.caption, item.sectorId ? (sectors?.find((s) => s.sectorId === item.sectorId)?.name ?? "Secteur") : "Tout le site"]
              .filter(Boolean)
              .join(" · ")
          : item.caption,
        leading: <img className={styles.thumb} src={mediaImage(item.mediaId, medias, "").src} alt="" />,
        badge: <Badge label={item.active ? "Actif" : "Inactif"} tone={item.active ? "accent" : "muted"} />,
        footer: item.linkTo ? <span className={styles.link}>Lien : {item.linkTo}</span> : undefined,
        actions: (
          <div className={styles.reorder}>
            <button
              type="button"
              className={styles.reorderButton}
              aria-label="Monter"
              disabled={index === 0 || move.isPending}
              onClick={() => move.mutate({ id: item.id, direction: "up" })}
            >
              ↑
            </button>
            <button
              type="button"
              className={styles.reorderButton}
              aria-label="Descendre"
              disabled={index === sortedItems.length - 1 || move.isPending}
              onClick={() => move.mutate({ id: item.id, direction: "down" })}
            >
              ↓
            </button>
          </div>
        ),
      })}
      toDraft={toDraft}
      renderFields={(draft, setDraft, item) => (
        <>
          {superAdmin && !item && (
            <Select label="Secteur" value={draft.sectorId} onChange={(sectorId) => setDraft({ ...draft, sectorId })} options={sectorOptions} />
          )}
          <FormField label="Titre" value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} required />
          <FormField label="Légende" value={draft.caption} onChange={(e) => setDraft({ ...draft, caption: e.target.value })} />
          <Select label="Image" value={draft.mediaId} onChange={(mediaId) => setDraft({ ...draft, mediaId })} options={options} />
          <FormField
            label="Lien (optionnel)"
            placeholder="/evenements ou /#benevolat"
            value={draft.linkTo}
            onChange={(e) => setDraft({ ...draft, linkTo: e.target.value })}
          />
          <Checkbox label="Actif" checked={draft.active} onChange={(active) => setDraft({ ...draft, active })} />
        </>
      )}
      onUpdate={(id, draft) => update.mutateAsync({ id, ...draftToInput(draft) })}
      create={{
        buttonLabel: "+ Nouvel élément",
        submitLabel: "Créer l'élément",
        emptyDraft,
        onCreate: (draft) => create.mutateAsync(draftToInput(draft)),
      }}
      remove={{
        title: "Supprimer cet élément du carrousel ?",
        message: (item) => `L'élément « ${item.title} » sera définitivement supprimé du carrousel.`,
        onRemove: (id) => remove.mutateAsync(id),
      }}
    />
  );
};

export default CarouselAdminPage;
