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
    // 1. 입력값 검증
    command.validate();

    // 2. 게시글 엔티티 생성 및 저장 (Transaction A 수행)
    Post post =
        Post.builder()
            .userId(command.userId())
            .type(command.type())
            .title(command.title())
            .content(command.content())
            .reward(command.reward())
            .imageUrls(command.imageUrls())
            .build();

    Post savedPost = postPersistencePort.savePost(post);

    // 3. 경험치 지급 시도 (Transaction A 일시중단 -> Transaction B 수행)
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

    // 4. 결과 반환 (Transaction A 커밋)
    String message = isXpGranted ? "게시글 작성 완료! (+" + grantedXp + " XP)" : "게시글 작성 완료";

    return new CreatePostResult(savedPost.getId(), isXpGranted, grantedXp, message);
  }
}
