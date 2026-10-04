import { Media } from "../medias/types";

/** Un Post est écrit en Brouillon (vu du seul Bureau), puis Publié pour tous. */
export type PostStatus = "brouillon" | "publie";

export const POST_STATUS_LABELS: Record<PostStatus, string> = {
  brouillon: "Brouillon",
  publie: "Publié",
};

/** Reflète NestedUserDto : le membre du Bureau qui a créé le Post. */
export interface PostAuthor {
  userId: string;
  firstName: string;
  lastName: string;
}

/** Reflète PostDto côté backend (vue Detailed). */
export interface Post {
  postId: string;
  title: string;
  content: string;
  status: PostStatus;
  /** Inconnu pour les Posts antérieurs aux auteurs. */
  author: PostAuthor | null;
  /** Le Secteur de son auteur ; null : tout le site (ADR 0004). */
  sectorId?: string | null;
  banner?: Media;
  attachments?: Media[];
}
