import { placeholderImage } from "../../lib/placeholderImage";
import { Post, PostAuthor } from "../../features/posts/types";

const BUREAU: PostAuthor = { userId: "user-3", firstName: "Claire", lastName: "Hoffmann" };

function banner(postId: string, label: string) {
  return {
    mediaId: `${postId}-banner`,
    fileKey: `posts/${postId}-banner.jpg`,
    originalFilename: `${postId}-banner.jpg`,
    contentType: "image/jpeg",
    fileSize: 180_000,
    url: placeholderImage(postId, label),
    alt: label,
  };
}

export const mockPosts: Post[] = [
  {
    postId: "post-1",
    status: "publie",
    sectorId: "sector-1",
    author: BUREAU,
    title: "Retour sur la collecte 2025",
    content:
      "Merci à tous les motards et bénévoles présents ce week-end ! Grâce à votre mobilisation, plus de 400 participants ont pris part à la balade solidaire au profit de la recherche contre le cancer. Rendez-vous l'an prochain pour une nouvelle édition.",
    banner: banner("post-1", "Collecte 2025"),
  },
  {
    postId: "post-2",
    status: "publie",
    sectorId: "sector-1",
    author: BUREAU,
    title: "Une Rose Un Espoir remet un chèque à l'hôpital d'Algrange",
    content:
      "Les fonds récoltés lors de nos actions 2024 ont permis de financer du matériel pour le service d'oncologie. Une cérémonie s'est tenue en présence du bureau de l'association et de l'équipe médicale.",
    banner: banner("post-2", "Remise de chèque"),
  },
  {
    postId: "post-3",
    status: "publie",
    sectorId: "sector-1",
    author: BUREAU,
    title: "Appel aux bénévoles pour l'édition 2026",
    content:
      "L'organisation de la prochaine collecte démarre ! Nous recherchons des bénévoles motivés pour la logistique, l'accueil et la communication. Rejoignez-nous.",
    banner: banner("post-3", "Appel aux bénévoles"),
  },
];
