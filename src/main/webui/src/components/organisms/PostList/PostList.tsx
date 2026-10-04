import React from "react";
import { usePosts } from "../../../features/posts/usePosts";
import MediaCard from "../../molecules/MediaCard/MediaCard";
import Spinner from "../../atoms/Spinner/Spinner";
import { placeholderImage } from "../../../lib/placeholderImage";
import { inSecteurChoice, useSecteurChoice } from "../../../features/sectors/secteurChoice";
import styles from "./PostList.module.css";

function excerpt(content: string, max = 140): string {
  return content.length > max ? `${content.slice(0, max).trimEnd()}…` : content;
}

export const PostList: React.FC = () => {
  const { data, isLoading, isError } = usePosts();
  const { sectorId: choice } = useSecteurChoice();
  const posts = data?.filter((post) => inSecteurChoice(choice, post.sectorId));

  if (isLoading) return <Spinner label="Chargement des actualités..." />;
  if (isError) return <p className={styles.error}>Impossible de charger les actualités pour le moment.</p>;
  if (!posts || posts.length === 0) return <p className={styles.empty}>Aucune actualité publiée pour le moment.</p>;

  return (
    <div className={styles.grid}>
      {posts.map((post) => (
        <MediaCard
          key={post.postId}
          to={`/actualites/${post.postId}`}
          imageSrc={post.banner?.url ?? placeholderImage(post.postId, post.title)}
          imageAlt={post.banner?.alt ?? post.title}
          title={post.title}
          excerpt={excerpt(post.content)}
        />
      ))}
    </div>
  );
};

export default PostList;
