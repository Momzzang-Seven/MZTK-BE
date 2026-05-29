package momzzangseven.mztkbe.modules.post.application.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final ApplicationEventPublisher eventPublisher;
  private final ZoneId appZoneId;

  @Override
  @Transactional
  public CreatePostResult execute(CreatePostCommand command) {
    if (command.type() != PostType.FREE) {
      throw new PostInvalidInputException("CreatePostService supports free posts only");
    }
    command.validate();
    validatePostImagesIfPresent(command);
    Post savedPost = savePost(command);
    // Publish inside this transaction; the level module grants XP on AFTER_COMMIT so the post
    // never holds a second connection while granting XP (see XpGrantEventHandler).
    eventPublisher.publishEvent(
        new PostCreatedEvent(
            command.userId(),
            savedPost.getId(),
            savedPost.getType(),
            LocalDateTime.now(appZoneId)));

    // TODO: grantedXp is hard-coded temporarily. MOM-465 decoupled granting xp logic with business logics due to Hikari connection occupation problem.
    return new CreatePostResult(savedPost.getId(), false, 30L, "게시글 작성 완료");
  }

  private void validatePostImagesIfPresent(CreatePostCommand command) {
    if (command.imageIds() == null || command.imageIds().isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(
        command.userId(), null, command.type(), command.imageIds());
  }

  private Post savePost(CreatePostCommand command) {
    // 1. 게시글 도메인 객체 생성
    Post post =
        Post.create(
            command.userId(),
            command.type(),
            command.title(),
            command.content(),
            command.reward(),
            command.tags());

    // 2. 게시글 저장
    Post savedPost = postPersistencePort.savePost(post);

    // 3. image module/tag module orchestration
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
