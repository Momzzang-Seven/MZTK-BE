package momzzangseven.mztkbe.modules.post.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.SavePostPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreatePostService implements CreatePostUseCase {

  private final SavePostPort savePostPort;
  private final GrantXpUseCase grantXpUseCase; // 레벨 모듈 연동!

  @Override
  public Long createPost(CreatePostCommand command) {
    // 1. 엔티티 생성
    Post post =
        Post.builder()
            .userId(command.userId())
            .type(command.type())
            .title(command.title())
            .content(command.content())
            .reward(command.reward()) // 질문이 아니면 null 들어감
            .build();

    // 2. DB 저장
    Post savedPost = savePostPort.savePost(post);

    // 3. 경험치(XP) 지급 (자유게시판인 경우)
    // 질문 게시판 작성 시 토큰 차감 로직은 추후 추가
    if (command.type() == PostType.FREE) {
      grantXpForPost(command.userId(), savedPost.getId());
    }

    // 4. 이미지 저장 로직은 여기에 추가

    return savedPost.getId();
  }

  // 경험치 지급 로직 분리
  private void grantXpForPost(Long userId, Long postId) {
    try {
      // 중복 지급 방지 키 (post:게시글ID)
      String idempotencyKey = "post:" + postId;

      GrantXpCommand xpCommand =
          GrantXpCommand.of(
              userId,
              XpType.POST, // 게시글 작성 보상
              LocalDateTime.now(),
              idempotencyKey,
              "Create Post ID: " + postId);

      grantXpUseCase.execute(xpCommand);

    } catch (Exception e) {
      // 경험치 지급 실패가 게시글 작성을 실패하게 만들면 안 됨 (로그만 남김)
      // log.error("Failed to grant XP for post creation", e);
      System.err.println("경험치 지급 실패: " + e.getMessage());
    }
  }
}
