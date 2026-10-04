import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changePostStatus, createPost, fetchManagedPosts, fetchPostById, fetchPosts, PostInput, updatePost } from "./postsApi";
import { Post, PostStatus } from "./types";
import { queryKeys } from "../queryKeys";

const published = (posts: Post[]) => posts.filter((p) => p.status === "publie");

/** Les Posts publiés, pour les pages publiques : même le Bureau n'y voit pas ses Brouillons. */
export function usePosts() {
  return useQuery({ queryKey: queryKeys.posts.all, queryFn: async () => published(await fetchPosts()) });
}

export function usePost(postId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.posts.detail(postId),
    queryFn: async () => {
      const post = await fetchPostById(postId as string);
      return post?.status === "publie" ? post : undefined;
    },
    enabled: Boolean(postId),
  });
}

/** Les Posts des Secteurs que gère la personne, Brouillons compris, pour l'écran Actualités. */
export function useAllPosts() {
  return useQuery({ queryKey: queryKeys.posts.admin, queryFn: fetchManagedPosts });
}

/** Écrire, modifier, publier et dépublier : réservé au Bureau. */
export function usePostMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.posts.all });

  const create = useMutation({
    mutationFn: (input: PostInput) => createPost(input),
    onSuccess: invalidate,
  });
  const update = useMutation({
    mutationFn: (input: { postId: string } & PostInput) => updatePost(input.postId, { title: input.title, content: input.content }),
    onSuccess: invalidate,
  });
  const changeStatus = useMutation({
    mutationFn: (input: { postId: string; status: PostStatus }) => changePostStatus(input.postId, input.status),
    onSuccess: invalidate,
  });

  return { create, update, changeStatus };
}
