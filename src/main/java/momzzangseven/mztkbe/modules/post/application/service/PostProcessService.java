package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
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

  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    command.validate();

    Post post = getPostOrThrow(postId);
    post.validateOwnership(currentUserId);

    post.update(command.title(), command.content(), command.imageUrls());
    postPersistencePort.savePost(post);
  }

  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = getPostOrThrow(postId);

    post.validateOwnership(currentUserId);

    postPersistencePort.deletePost(post);
  }

  private Post getPostOrThrow(Long postId) {
    return postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);
  }
}
