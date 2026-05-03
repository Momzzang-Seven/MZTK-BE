package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.global.error.post.PostPublicationStateException;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.RecoverQuestionPostEscrowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recreates a question-create intent for a local question post that is still missing projection.
 */
@Service
@RequiredArgsConstructor
public class RecoverQuestionPostEscrowService implements RecoverQuestionPostEscrowUseCase {

  private final PostPersistencePort postPersistencePort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  private final LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;
  private final CountAnswersPort countAnswersPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final LinkTagPort linkTagPort;
  private final PostVisibilityPolicy postVisibilityPolicy;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  @Override
  public PostMutationResult recoverQuestionCreate(RecoverQuestionPostEscrowCommand command) {
    command.validate();

    RecoveryPayload validatedPayload =
        runInTransaction(
            () -> {
              Post post = loadAndValidateRecoveryTarget(command);
              return new RecoveryPayload(post.getContent(), post.getReward());
            });
    boolean managedLifecycle = questionLifecycleExecutionPort.managesQuestionCreateLifecycle();
    if (!managedLifecycle) {
      validateUnmanagedRecovery(command);
      return new PostMutationResult(
          command.postId(),
          questionLifecycleExecutionPort
              .recoverQuestionCreate(
                  command.postId(),
                  command.requesterId(),
                  validatedPayload.questionContent(),
                  validatedPayload.rewardMztk())
              .orElse(null));
    }

    RecoveryPayload payload = runInTransaction(() -> prepareManagedRecovery(command));
    QuestionExecutionWriteView web3 =
        questionLifecycleExecutionPort
            .recoverQuestionCreate(
                command.postId(),
                command.requesterId(),
                payload.questionContent(),
                payload.rewardMztk())
            .orElse(null);
    recordPreparedCreateIntentIfPresent(command.postId(), web3);

    return new PostMutationResult(command.postId(), web3);
  }

  private void validateUnmanagedRecovery(RecoverQuestionPostEscrowCommand command) {
    if (command.hasMutationFields()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_CREATE_RECOVERY_UNAVAILABLE);
    }
  }

  private RecoveryPayload prepareManagedRecovery(RecoverQuestionPostEscrowCommand command) {
    Post post = loadAndValidateRecoveryTarget(command);
    validateManagedRecoveryEvidence(command, post);

    Post recoveryPost = applyOptionalEdit(command, post).markPublicationPending();
    postPersistencePort.savePost(recoveryPost);
    syncTagsAndImages(command, recoveryPost);
    return new RecoveryPayload(recoveryPost.getContent(), recoveryPost.getReward());
  }

  private Post loadAndValidateRecoveryTarget(RecoverQuestionPostEscrowCommand command) {
    Post post =
        postPersistencePort
            .loadPostForUpdate(command.postId())
            .orElseThrow(PostNotFoundException::new);
    post.validateOwnership(command.requesterId());
    postVisibilityPolicy.validateOwnerMutationAllowed(post);
    if (post.getType() != PostType.QUESTION) {
      throw new PostInvalidInputException("Only question posts support escrow recovery.");
    }
    if (!post.isPublicationFailed()) {
      throw new PostInvalidInputException("Only failed question posts support create recovery.");
    }
    return post;
  }

  private void validateManagedRecoveryEvidence(
      RecoverQuestionPostEscrowCommand command, Post post) {
    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), command.requesterId());
    if (evidence.projectionExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }
    if (evidence.activeCreateIntentExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }
    if (!evidence.terminalCreateIntentExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_CREATE_RECOVERY_UNAVAILABLE);
    }
  }

  private Post applyOptionalEdit(RecoverQuestionPostEscrowCommand command, Post post) {
    if (!command.hasMutationFields()) {
      return post;
    }
    long activeAnswerCount = countAnswersPort.countAnswers(post.getId());
    validatePostImagesIfPresent(
        command.requesterId(), post.getId(), post.getType(), command.imageIds());
    return post.update(command.title(), command.content(), command.tags(), activeAnswerCount);
  }

  private void syncTagsAndImages(RecoverQuestionPostEscrowCommand command, Post post) {
    if (!command.hasMutationFields()) {
      return;
    }
    if (command.tags() != null) {
      linkTagPort.updateTags(post.getId(), command.tags());
    }
    if (command.imageIds() != null) {
      updatePostImagesPort.updateImages(
          command.requesterId(), post.getId(), post.getType(), command.imageIds());
    }
  }

  private void validatePostImagesIfPresent(
      Long userId, Long postId, PostType postType, List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(userId, postId, postType, imageIds);
  }

  private void recordPreparedCreateIntentIfPresent(Long postId, QuestionExecutionWriteView web3) {
    if (web3 == null
        || web3.executionIntent() == null
        || web3.executionIntent().id() == null
        || web3.executionIntent().id().isBlank()) {
      return;
    }
    runInTransaction(
        () -> {
          postPersistencePort
              .loadPostForUpdate(postId)
              .filter(post -> post.getType() == PostType.QUESTION)
              .map(post -> post.markPublicationPending(web3.executionIntent().id()))
              .ifPresent(postPersistencePort::savePost);
          return null;
        });
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record RecoveryPayload(String questionContent, Long rewardMztk) {}
}
