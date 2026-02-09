package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.port.out.SavePostPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final LoadPostPort loadPostPort;
  private final SavePostPort savePostPort;

  // [Update] 게시글 수정
  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostRequest request) {
    Post post = getPostOrThrow(postId);

    // 권한 체크: 작성자 본인인지 확인
    checkOwnership(post, currentUserId);

    post.update(request.title(), request.content());

    savePostPort.savePost(post);
  }

  // [Delete] 게시글 삭제
  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = getPostOrThrow(postId);

    checkOwnership(post, currentUserId);

    savePostPort.deletePost(post);
  }

  // 공통 메서드: 게시글 조회
  private Post getPostOrThrow(Long postId) {
    return loadPostPort
        .loadPost(postId)
        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
  }

  // 공통 메서드: 권한 체크
  private void checkOwnership(Post post, Long currentUserId) {
    if (!post.getUserId().equals(currentUserId)) {
      throw new IllegalStateException("본인의 게시글만 수정/삭제할 수 있습니다."); // 403 Forbidden 대용
    }
  }
}
