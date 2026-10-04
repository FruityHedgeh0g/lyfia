package fr.fruityhedgeh0g.services.decorators.logs;

import fr.fruityhedgeh0g.dtos.postDtos.PostDto;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.services.interfaces.PostService;
import io.quarkus.logging.Log;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@Priority(200)
@Decorator
public class PostLogDecorator implements PostService {

    @Inject
    @Delegate
    PostService postService;

    @Override
    public List<PostDto> listAll(boolean seesDrafts, boolean managed) {
        Log.debugf("Retrieving all posts...");
        return Try.of(() -> postService.listAll(seesDrafts, managed))
                .onSuccess(posts -> Log.debugf("%d posts retrieved.",posts.size()))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving posts."))
                .get();
    }

    @Override
    public PostDto getById(UUID postId, boolean seesDrafts) {
        Log.debugf("Retrieving post by id %s...",postId);
        return Try.of(() -> postService.getById(postId, seesDrafts))
                .onSuccess(post -> Log.debugf("Post retrieved: "+post.toString()))
                .onFailure(t -> {
                    switch(t){
                        case UnknownResourceException ex -> Log.warnf("Post with id %s not found.", postId);
                        default -> Log.errorf(t,"An error occurred while retrieving post.");
                    }
                })
                .get();
    }

    @Override
    public PostDto create(PostDto postDto, UUID authorId) {
        return logged(() -> postService.create(postDto, authorId), "Creation of a post by " + authorId);
    }

    @Override
    public PostDto update(PostDto postDto) {
        return logged(() -> postService.update(postDto), "Update of post " + postDto.getPostId());
    }

    @Override
    public PostDto changeStatus(UUID postId, PostStatusEnum status) {
        return logged(() -> postService.changeStatus(postId, status), "Post " + postId + " set to " + status.id());
    }

    @Override
    public void delete(UUID postId) {
        logged(() -> {
            postService.delete(postId);
            return null;
        }, "Deletion of post " + postId);
    }

    /** Logs a Post action: refusals as warnings, anything else as errors. */
    private <T> T logged(CheckedFunction0<T> action, String what) {
        Log.debugf("%s...", what);
        return Try.of(action)
                .onSuccess(r -> Log.debugf("%s: done.", what))
                .onFailure(t -> {
                    switch (t) {
                        case InvalidResourceException ex -> Log.warnf("%s refused: %s", what, ex.getMessage());
                        case UnknownResourceException ex -> Log.warnf("%s refused: %s", what, ex.getMessage());
                        default -> Log.errorf(t, "%s failed.", what);
                    }
                })
                .get();
    }
}
