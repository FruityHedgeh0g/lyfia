import { Post, PostStatus } from "./types";
import { apiFetch, HttpError } from "../../lib/http";

/** Ce que le Bureau écrit ; statut, auteur et Secteur sont fixés par l'API (le Super admin choisit le Secteur). */
export interface PostInput {
  title: string;
  content: string;
  sectorId?: string | null;
}

type PostDto = Omit<Post, "content" | "author"> & { content?: string | null; author?: Post["author"] };

const toPost = (dto: PostDto): Post => ({ ...dto, content: dto.content ?? "", author: dto.author ?? null });

const base = (postId: string) => `/api/posts/${encodeURIComponent(postId)}`;

/**
 * Les actualités, sur PostController. L'API ne renvoie les Brouillons qu'au Bureau ; un nouveau Post est un
 * Brouillon signé par la personne connectée, puis publié (`publie`) ou repassé en brouillon (`brouillon`).
 * Lire les actualités publiées ne demande pas d'être connecté.
 */
export async function fetchPosts(): Promise<Post[]> {
  return (await apiFetch<PostDto[]>("/api/posts")).map(toPost);
}

/** Les Posts des Secteurs que gère la personne (tous pour le Super admin), Brouillons compris : l'écran Actualités. */
export async function fetchManagedPosts(): Promise<Post[]> {
  return (await apiFetch<PostDto[]>("/api/posts?managed=true")).map(toPost);
}

/** undefined pour un Post inconnu, ou un Brouillon quand on n'est pas du Bureau. */
export async function fetchPostById(postId: string): Promise<Post | undefined> {
  try {
    return toPost(await apiFetch<PostDto>(base(postId)));
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}

export async function createPost(input: PostInput): Promise<Post> {
  return toPost(await apiFetch<PostDto>("/api/posts", { method: "POST", body: JSON.stringify(input) }));
}

export async function updatePost(postId: string, patch: PostInput): Promise<Post> {
  const { title, content } = patch;
  return toPost(await apiFetch<PostDto>("/api/posts", { method: "PATCH", body: JSON.stringify({ postId, title, content }) }));
}

export async function changePostStatus(postId: string, status: PostStatus): Promise<Post> {
  return toPost(await apiFetch<PostDto>(`${base(postId)}/status`, { method: "PUT", body: JSON.stringify({ status }) }));
}
