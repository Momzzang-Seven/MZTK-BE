package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.api.dto.PostResponse;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용이므로 readOnly 최적화
public class GetPostService implements GetPostUseCase {

  private final LoadPostPort loadPostPort;

  @Override
  @Transactional // 조회수 증가 때문에 쓰기 권한 필요
  public PostResponse getPost(Long postId) {
    // 1. 게시글 조회 (없으면 예외 발생)
    Post post =
        loadPostPort
            .loadPost(postId)
            .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. ID: " + postId));

    // 2. 조회수 증가 (도메인 로직 실행)
    post.increaseViewCount();

    // 3. DTO 변환 및 반환
    return PostResponse.from(post);
  }
}
