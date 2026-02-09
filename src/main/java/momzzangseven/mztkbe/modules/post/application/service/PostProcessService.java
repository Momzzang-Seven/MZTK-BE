package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final PostPersistencePort postPersistencePort;

  // [Update] 게시글 수정
  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    Post post = getPostOrThrow(postId);
    checkOwnership(post, currentUserId);
    post.update(command.title(), command.content(), command.imageUrls());

    postPersistencePort.savePost(post);
  }

  // [Delete] 게시글 삭제
  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = getPostOrThrow(postId);
    checkOwnership(post, currentUserId);

    postPersistencePort.deletePost(post);
  }

  private Post getPostOrThrow(Long postId) {
    return postPersistencePort
        .loadPost(postId)
        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
  }

  private void checkOwnership(Post post, Long currentUserId) {
    if (!post.getUserId().equals(currentUserId)) {
      // 나중에 Custom Exception(예: ForbiddenException)으로 교체하면 더 좋습니다.
      throw new IllegalStateException("본인의 게시글만 수정/삭제할 수 있습니다.");
    }
  }
}
