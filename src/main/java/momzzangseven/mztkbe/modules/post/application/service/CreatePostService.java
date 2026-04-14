package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
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

  @Override
  @Transactional
  public CreatePostResult execute(CreatePostCommand command) {
    if (command.type() != PostType.FREE) {
      throw new PostInvalidInputException("CreatePostService supports free posts only");
    }
    command.validate();
    Post savedPost = savePost(command);
    XpGrantResult xpResult = grantCreateXp(command.userId(), savedPost.getId());
    return new CreatePostResult(
        savedPost.getId(), xpResult.isXpGranted(), xpResult.grantedXp(), xpResult.message());
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

  private XpGrantResult grantCreateXp(Long userId, Long postId) {
    Long grantedXp = 0L;
    boolean isXpGranted = false;

    try {
      grantedXp = postXpService.grantCreatePostXp(userId, postId);
      if (grantedXp > 0) {
        isXpGranted = true;
      }
    } catch (Exception e) {
      log.warn("Post created but XP grant failed for user: {}", userId, e);
    }

    String message = isXpGranted ? "게시글 작성 완료! (+" + grantedXp + " XP)" : "게시글 작성 완료";
    return new XpGrantResult(isXpGranted, grantedXp, message);
  }

  private record XpGrantResult(boolean isXpGranted, Long grantedXp, String message) {}
}
