package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;
  private final LinkTagPort linkTagPort;

  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    command.validate();

    Post post = getPostOrThrow(postId);
    post.validateOwnership(currentUserId);

    Post updatedPost =
        post.update(command.title(), command.content(), command.imageUrls(), command.tags());
    postPersistencePort.savePost(updatedPost);
    if (command.tags() != null) {
      linkTagPort.updateTags(postId, command.tags());
    }
  }

  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);

    post.validateOwnership(currentUserId);
    postPersistencePort.deletePost(post);

    eventPublisher.publishEvent(new PostDeletedEvent(postId));
  }

  private Post getPostOrThrow(Long postId) {
    return postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);
  }
}
