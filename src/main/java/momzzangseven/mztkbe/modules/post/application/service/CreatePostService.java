package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a free-board post (T1).
 *
 * <p>This is the transaction boundary for the entity write only — XP granting is orchestrated
 * separately by {@link CreateFreePostFacade} so the request holds at most one DB connection at a
 * time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePostService {

  private final PostPersistencePort postPersistencePort;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;

  /** Saves a free post and links its images/tags, returning the new post id. */
  @Transactional
  public Long createFreePost(CreatePostCommand command) {
    if (command.type() != PostType.FREE) {
      throw new PostInvalidInputException("CreatePostService supports free posts only");
    }
    command.validate();
    validatePostImagesIfPresent(command);
    return savePost(command).getId();
  }

  private void validatePostImagesIfPresent(CreatePostCommand command) {
    if (command.imageIds() == null || command.imageIds().isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(
        command.userId(), null, command.type(), command.imageIds());
  }

  private Post savePost(CreatePostCommand command) {
    Post post =
        Post.create(
            command.userId(),
            command.type(),
            command.title(),
            command.content(),
            command.reward(),
            command.tags());

    Post savedPost = postPersistencePort.savePost(post);

    if (command.imageIds() != null && !command.imageIds().isEmpty()) {
      updatePostImagesPort.updateImages(
          savedPost.getUserId(), savedPost.getId(), savedPost.getType(), command.imageIds());
    }

    if (command.tags() != null && !command.tags().isEmpty()) {
      linkTagPort.linkTagsToPost(savedPost.getId(), command.tags());
    }

    return savedPost;
  }
}
