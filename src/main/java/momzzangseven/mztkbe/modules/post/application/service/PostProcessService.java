package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
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

  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    Post post = getPostOrThrow(postId);
    checkOwnership(post, currentUserId);

    post.update(command.title(), command.content());
    savePostPort.savePost(post);
  }

  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = getPostOrThrow(postId);
    checkOwnership(post, currentUserId);

    savePostPort.deletePost(post);
  }

  private Post getPostOrThrow(Long postId) {
    return loadPostPort
        .loadPost(postId)
        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
  }

  private void checkOwnership(Post post, Long currentUserId) {
    if (!post.getUserId().equals(currentUserId)) {
      throw new IllegalStateException("본인의 게시글만 수정/삭제할 수 있습니다.");
    }
  }
}
