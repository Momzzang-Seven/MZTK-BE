package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.RecoverQuestionPostEscrowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recreates a question-create intent for a local question post that is still missing projection.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecoverQuestionPostEscrowService implements RecoverQuestionPostEscrowUseCase {

  private final PostPersistencePort postPersistencePort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Override
  public PostMutationResult recoverQuestionCreate(RecoverQuestionPostEscrowCommand command) {
    command.validate();

    Post post = postPersistencePort.loadPost(command.postId()).orElseThrow(PostNotFoundException::new);
    post.validateOwnership(command.requesterId());
    if (post.getType() != PostType.QUESTION) {
      throw new momzzangseven.mztkbe.global.error.post.PostInvalidInputException(
          "Only question posts support escrow recovery.");
    }

    return new PostMutationResult(
        command.postId(),
        questionLifecycleExecutionPort
            .recoverQuestionCreate(
                command.postId(), command.requesterId(), post.getContent(), post.getReward())
            .orElse(null));
  }
}
