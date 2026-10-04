package fr.fruityhedgeh0g.services.interfaces.publics;

import fr.fruityhedgeh0g.dtos.postDtos.PostDto;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicPostService {

    /** @param seesDrafts true for the Bureau and above; everyone else sees only Publié Posts */
    /**
     * Publié Posts, plus the Brouillons of the Secteurs this person manages when they see drafts; {@code managed}:
     * only the Posts of the Secteurs this person manages (the Actualités admin screen).
     */
    List<PostDto> listAll(boolean seesDrafts, boolean managed);
    PostDto getById(@NotNull UUID postId, boolean seesDrafts);
    /** A new Post is Brouillon, with its creator as author. */
    PostDto create(@NotNull @Valid PostDto postDto, @NotNull UUID authorId);
    PostDto update(@NotNull @Valid PostDto postDto);
    /** Publishes or unpublishes. */
    PostDto changeStatus(@NotNull UUID postId, @NotNull PostStatusEnum status);
    void delete(@NotNull UUID postId);

//    Try<List<PostDto>> getAllPosts();
//    Try<PostDto> getPostById(@NotNull UUID postId);
//    Try<PostDto> createPost(@NotNull @Valid PostDto postDto);
//    Try<PostDto> updatePost(@NotNull @Valid PostDto postDto);
//    Try<Void> deletePost(@NotNull UUID postId);
//    Try<PostDto> addPostBanner(@NotNull UUID postId, @NotNull UUID bannerId);
//    Try<PostDto> deletePostBanner(@NotNull UUID postId);
//    Try<PostDto> updatePostBanner(@NotNull UUID postId, @NotNull UUID tagId);
//    Try<PostDto> addPostAttachment(@NotNull UUID postId, @NotNull UUID attachmentId);
//    Try<PostDto> deletePostAttachment(@NotNull UUID postId, @NotNull UUID attachmentId);


}
