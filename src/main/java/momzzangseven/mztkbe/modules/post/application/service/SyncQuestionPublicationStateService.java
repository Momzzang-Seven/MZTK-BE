package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionCreatedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.FailQuestionCreateUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncQuestionPublicationStateService
    implements ConfirmQuestionCreatedUseCase, FailQuestionCreateUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional
  public void confirmQuestionCreated(SyncQuestionPublicationStateCommand command) {
    command.validateForConfirm();
    postPersistencePort
        .loadPostForUpdate(command.postId())
        .filter(this::isQuestionPost)
        .map(Post::markPublicationVisible)
        .ifPresent(postPersistencePort::savePost);
  }

  @Override
  @Transactional
  public void failQuestionCreate(SyncQuestionPublicationStateCommand command) {
    command.validateForFailure();
    postPersistencePort
        .loadPostForUpdate(command.postId())
        .filter(this::isQuestionPost)
        .filter(post -> post.getPublicationStatus() != PostPublicationStatus.VISIBLE)
        .map(Post::markPublicationFailed)
        .ifPresent(postPersistencePort::savePost);
  }

  private boolean isQuestionPost(Post post) {
    return post.getType() == PostType.QUESTION;
  }
}
