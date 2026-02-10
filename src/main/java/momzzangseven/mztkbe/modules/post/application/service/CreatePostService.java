package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final PostXpService postXpService;

  @Override
  @Transactional // Transaction A 시작
  public CreatePostResult createPost(CreatePostCommand command) {

    command.validate();

    Post post =
        Post.create(
            command.userId(),
            command.type(),
            command.title(),
            command.content(),
            command.reward(),
            command.imageUrls());

    Post savedPost = postPersistencePort.savePost(post);

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
