package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final PostXpService postXpService;
  private final LinkTagPort linkTagPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Override
  @Transactional
  public CreatePostResult execute(CreatePostCommand command) {

    command.validate();
    if (command.type() == PostType.QUESTION) {
      questionLifecycleExecutionPort.precheckQuestionCreate(command.userId(), command.reward());
    }

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

    if (savedPost.getType() == PostType.QUESTION) {
      questionLifecycleExecutionPort.prepareQuestionCreate(
          savedPost.getId(), savedPost.getUserId(), savedPost.getContent(), savedPost.getReward());
    }

    Long grantedXp = 0L;
    boolean isXpGranted = false;

    try {
      grantedXp = postXpService.grantCreatePostXp(command.userId(), savedPost.getId());

      if (grantedXp > 0) {
        isXpGranted = true;
      }
    } catch (Exception e) {
      log.warn("Post created but XP grant failed for user: {}", command.userId(), e);
    }

    String message = isXpGranted ? "게시글 작성 완료! (+" + grantedXp + " XP)" : "게시글 작성 완료";

    return new CreatePostResult(savedPost.getId(), isXpGranted, grantedXp, message);
  }
}
