package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.security.SecteurScope;
import fr.fruityhedgeh0g.security.Viewer;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalSectorService;
import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.postDtos.PostDto;
import fr.fruityhedgeh0g.entities.PostEntity;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.NotImplementedYetException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.PostRepository;
import fr.fruityhedgeh0g.services.interfaces.PostService;
import fr.fruityhedgeh0g.services.interfaces.internals.InternalUserService;
import fr.fruityhedgeh0g.utilities.mappers.PostMapper;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class PostServiceImpl implements PostService {
    @Inject
    PostRepository postRepository;

    @Inject
    PostMapper postMapper;

    @Inject
    InternalUserService internalUserService;

    @Inject
    InternalSectorService internalSectorService;

    @Inject
    Viewer viewer;

    @Inject
    FeatureLevers featureLevers;

    @Override
    public List<PostDto> listAll(boolean seesDrafts, boolean managed) {
        SecteurScope scope = viewer.scope();
        // A Secteur whose Actualités are off keeps its Posts away from the public (ADR 0009)
        Set<UUID> hidden = featureLevers.hiddenFromPublic(FeatureEnum.ACTUALITES);
        return postRepository.listAll().stream()
                .filter(this::visible)
                .filter(post -> post.getSector() == null || !hidden.contains(post.getSector().getSectorId()))
                .filter(post -> managed ? scope.covers(post.getSector()) : readable(post, seesDrafts, scope))
                .map(postMapper::toDto)
                .toList();
    }

    @Override
    public PostDto getById(UUID postId, boolean seesDrafts) {
        SecteurScope scope = viewer.scope();
        PostEntity post = postRepository.findByIdOptional(postId)
                .filter(this::visible)
                .filter(p -> readable(p, seesDrafts, scope))
                .orElseThrow(() -> new UnknownResourceException("Post not found: "+postId));
        featureLevers.requireForPublic(FeatureEnum.ACTUALITES, post.getSector());
        return postMapper.toDto(post);
    }

    /**
     * A Post is written for its author's Secteur (ADR 0004); the Super admin, above the Secteurs, chooses one, or
     * none for the whole site.
     */
    @Override
    @Transactional
    public PostDto create(PostDto postDto, UUID authorId) {
        PostEntity post = postMapper.toEntity(postDto);
        post.setStatus(PostStatusEnum.BROUILLON);
        UserEntity author = internalUserService.doGetEntityById(authorId)
                .orElseThrow(() -> new UnknownResourceException("User not found: " + authorId));
        post.setAuthor(author);
        post.setSector(sectorFor(author, postDto.getSectorId()));
        validate(post);
        postRepository.persist(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional
    public PostDto update(PostDto postDto) {
        if (postDto.getPostId() == null) throw new InvalidResourceException("Missing post id.");
        PostEntity post = managedPostOrThrow(postDto.getPostId());
        postMapper.partialDtoToEntity(post, postDto);
        validate(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional
    public PostDto changeStatus(UUID postId, PostStatusEnum status) {
        PostEntity post = managedPostOrThrow(postId);
        post.setStatus(status);
        return postMapper.toDto(post);
    }

    private SectorEntity sectorFor(UserEntity author, UUID chosen) {
        if (!viewer.scope().everySecteur()) {
            if (author.getSector() == null) throw new ForbiddenActionException("A Post is written for its author's Secteur.");
            return author.getSector();
        }
        if (chosen == null) return null;
        SectorEntity sector = internalSectorService.doGetEntityById(chosen)
                .orElseThrow(() -> new UnknownResourceException("Sector not found: " + chosen));
        if (sector.isClosed()) throw new InvalidResourceException("A Secteur fermé gets no new Post.");
        return sector;
    }

    /** Everyone reads Publié Posts; Brouillons, only the Bureau of their Secteur (the Super admin: all). */
    private static boolean readable(PostEntity post, boolean seesDrafts, SecteurScope scope) {
        return post.isPublished() || (seesDrafts && scope.covers(post.getSector()));
    }

    /** Only the Super admin sees what belongs to a Secteur fermé (ADR 0003). */
    private boolean visible(PostEntity post) {
        return !post.isInClosedSector() || viewer.seesClosedSecteurs();
    }

    /** Only the Bureau of the Post's Secteur edits, publishes and unpublishes it; the Super admin, any Post. */
    private PostEntity managedPostOrThrow(UUID postId) {
        PostEntity post = postRepository.findByIdOptional(postId)
                .filter(this::visible)
                .orElseThrow(() -> new UnknownResourceException("Post not found: " + postId));
        if (!viewer.scope().covers(post.getSector()))
            throw new ForbiddenActionException("A Post is managed by its own Secteur's Bureau.");
        if (post.isInClosedSector()) throw new InvalidResourceException("A Secteur fermé is read-only.");
        return post;
    }

    /** A Post has a title and a content. */
    private static void validate(PostEntity post) {
        if (post.getTitle() == null || post.getTitle().isBlank())
            throw new InvalidResourceException("A Post has a title.");
        if (post.getContent() == null || post.getContent().isBlank())
            throw new InvalidResourceException("A Post has a content.");
    }

    @Override
    @Transactional
    public void delete(UUID postId) {
        throw new NotImplementedYetException(this.getClass().getSimpleName());
    }

//    @Override
//    @Transactional
//    public Try<List<PostDto>> getAllPosts() {
//        Log.info("Getting all posts");
//        return Try.of(() -> postRepository
//                .findAll()
//                .stream()
//                .map(postMapper::toDto)
//                .toList())
//                .onFailure(e ->
//                        Log.error("Error getting all posts", e)
//                );
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> getPostById( UUID postId) {
//        Log.infof("Getting post with id: %s", postId);
//        return Try.of(() -> postRepository
//                .findByIdOptional(postId)
//                .orElseThrow(() -> new UnknownResourceException("Post not found: " + postId)))
//                .map(postMapper::toDto)
//                .onFailure(e -> {
//                    if (e instanceof UnknownResourceException ex) {
//                        Log.warn(ex.getMessage());
//                    } else {
//                        Log.errorf(e, "Error getting post with id: %s", postId);
//                    }
//                });
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> createPost( PostDto postDto) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> updatePost( PostDto postDto) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<Void> deletePost( UUID postId) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> addPostBanner(UUID postId, UUID bannerId) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> deletePostBanner(UUID postId) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> updatePostBanner(UUID postId, UUID tagId) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> addPostAttachment(UUID postId, UUID attachmentId) {
//        return null;
//    }
//
//    @Override
//    @Transactional
//    public Try<PostDto> deletePostAttachment(UUID postId, UUID attachmentId) {
//        return null;
//    }

}
