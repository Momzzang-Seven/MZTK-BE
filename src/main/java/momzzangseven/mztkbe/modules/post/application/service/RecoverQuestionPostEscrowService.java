package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerCreateIntentConflictPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
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
  private final LoadAnswerCreateIntentConflictPort loadAnswerCreateIntentConflictPort;
  private final LoadTagPort loadTagPort;
  private final LoadPostImagesPort loadPostImagesPort;
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

    PostMutationResult duplicateRecovery =
        runInTransaction(() -> tryManagedDuplicateRecovery(command)).orElse(null);
    if (duplicateRecovery != null) {
      return duplicateRecovery;
    }

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

    RecoveryPreparation preparation = runInTransaction(() -> prepareManagedRecovery(command));
    QuestionExecutionWriteView web3 = recoverManagedQuestionCreate(command, preparation);
    try {
      runInTransaction(
          () -> {
            finalizeManagedRecovery(command, preparation, web3);
            return null;
          });
    } catch (RuntimeException ex) {
      if (isFinalizedToExpectedCreateIntent(command, preparation, preparedCreateIntentId(web3))) {
        return new PostMutationResult(command.postId(), web3);
      }
      cancelPreparedIntentIfOwned(web3, "question recover create intent bind failed");
      throw ex;
    }

    return new PostMutationResult(command.postId(), web3);
  }

  private Optional<PostMutationResult> tryManagedDuplicateRecovery(
      RecoverQuestionPostEscrowCommand command) {
    Post post = loadAndValidateOwnedQuestion(command);
    if (!post.isPublicationPending() || post.getCurrentCreateExecutionIntentId() == null) {
      return Optional.empty();
    }
    if (!questionLifecycleExecutionPort.managesQuestionCreateLifecycle()) {
      return Optional.empty();
    }
    if (command.hasMutationFields()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }

    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), command.requesterId());
    if (evidence.projectionExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }
    if (!evidence.activeCreateIntentExists()
        || !evidence.hasLatestCreateExecutionIntent(post.getCurrentCreateExecutionIntentId())) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }
    if (!"AWAITING_SIGNATURE".equals(evidence.latestCreateIntentStatus())) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }

    QuestionExecutionWriteView web3 =
        questionLifecycleExecutionPort
            .loadQuestionCreateIntent(
                post.getId(),
                command.requesterId(),
                post.getCurrentCreateExecutionIntentId(),
                post.getContent(),
                post.getReward())
            .orElseThrow(
                () ->
                    new PostPublicationStateException(
                        ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT));
    if (preparedCreateIntentId(web3) == null
        || !"AWAITING_SIGNATURE".equals(web3.executionIntent().status())) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }
    return Optional.of(new PostMutationResult(post.getId(), web3));
  }

  private void validateUnmanagedRecovery(RecoverQuestionPostEscrowCommand command) {
    if (command.hasMutationFields()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_CREATE_RECOVERY_UNAVAILABLE);
    }
  }

  private RecoveryPreparation prepareManagedRecovery(RecoverQuestionPostEscrowCommand command) {
    Post post = loadAndValidateRecoveryTarget(command);
    validateManagedRecoveryEvidence(command, post);

    long activeAnswerCount = 0L;
    Post recoveryPost = post;
    if (command.hasMutationFields()) {
      activeAnswerCount = countActiveAnswers(post);
      validatePostImagesIfPresent(
          command.requesterId(), post.getId(), post.getType(), command.imageIds());
      recoveryPost =
          post.update(command.title(), command.content(), command.tags(), activeAnswerCount);
    }
    return RecoveryPreparation.from(post, recoveryPost, activeAnswerCount);
  }

  private QuestionExecutionWriteView recoverManagedQuestionCreate(
      RecoverQuestionPostEscrowCommand command, RecoveryPreparation preparation) {
    QuestionExecutionWriteView web3 =
        questionLifecycleExecutionPort
            .recoverQuestionCreate(
                command.postId(),
                command.requesterId(),
                preparation.questionContent(),
                preparation.rewardMztk())
            .orElse(null);
    if (preparedCreateIntentId(web3) == null) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_CREATE_RECOVERY_UNAVAILABLE);
    }
    return web3;
  }

  private void finalizeManagedRecovery(
      RecoverQuestionPostEscrowCommand command,
      RecoveryPreparation preparation,
      QuestionExecutionWriteView web3) {
    String executionIntentId = preparedCreateIntentId(web3);
    Post post = loadAndValidateOwnedQuestion(command);
    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), command.requesterId());
    validatePreparedCreateIntentEvidence(evidence, executionIntentId);

    int updatedRows =
        postPersistencePort.updateQuestionPublicationStateIfExpected(
            post.getId(),
            preparation.expectedPublicationStatus(),
            preparation.expectedCurrentCreateExecutionIntentId(),
            preparation.expectedPublicationFailureTerminalStatus(),
            preparation.expectedPublicationFailureReason(),
            PostPublicationStatus.PENDING,
            executionIntentId,
            null,
            null);
    if (updatedRows == 0) {
      if (isFinalizedToExpectedCreateIntent(command, preparation, executionIntentId)) {
        return;
      }
      cancelPreparedIntentIfOwned(web3, "question recover create intent bind failed");
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }

    Post claimedPost = post.markPublicationPending(executionIntentId);
    if (hasPostAggregateEdit(command)) {
      Post editedPost =
          claimedPost.update(
              command.title(), command.content(), command.tags(), preparation.activeAnswerCount());
      postPersistencePort.savePost(editedPost);
    }
    syncTagsAndImages(command, claimedPost);
  }

  private Post loadAndValidateRecoveryTarget(RecoverQuestionPostEscrowCommand command) {
    Post post = loadAndValidateOwnedQuestion(command);
    if (!post.isPublicationFailed()) {
      throw new PostInvalidInputException("Only failed question posts support create recovery.");
    }
    return post;
  }

  private Post loadAndValidateOwnedQuestion(RecoverQuestionPostEscrowCommand command) {
    Post post =
        postPersistencePort
            .loadPostForUpdate(command.postId())
            .orElseThrow(PostNotFoundException::new);
    post.validateOwnership(command.requesterId());
    postVisibilityPolicy.validateOwnerMutationAllowed(post);
    if (post.getType() != PostType.QUESTION) {
      throw new PostInvalidInputException("Only question posts support escrow recovery.");
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

  private void validatePreparedCreateIntentEvidence(
      QuestionPublicationEvidence evidence, String executionIntentId) {
    if (evidence.projectionExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }
    if (evidence.activeCreateIntentExists()
        && evidence.hasLatestCreateExecutionIntent(executionIntentId)) {
      return;
    }
    throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
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

  private long countActiveAnswers(Post post) {
    if (!PostType.QUESTION.equals(post.getType())) {
      return 0L;
    }
    long localAnswerCount = countAnswersPort.countOnchainBlockingAnswers(post.getId());
    if (localAnswerCount > 0) {
      return localAnswerCount;
    }
    if (countAnswersPort.existsPreparingOrPendingCreateByPostId(post.getId())) {
      return 1L;
    }
    return loadAnswerCreateIntentConflictPort.hasActiveAnswerCreateIntent(post.getId()) ? 1L : 0L;
  }

  private String preparedCreateIntentId(QuestionExecutionWriteView web3) {
    if (web3 == null
        || web3.executionIntent() == null
        || web3.executionIntent().id() == null
        || web3.executionIntent().id().isBlank()) {
      return null;
    }
    return web3.executionIntent().id();
  }

  private boolean isFinalizedToExpectedCreateIntent(
      RecoverQuestionPostEscrowCommand command,
      RecoveryPreparation preparation,
      String executionIntentId) {
    if (executionIntentId == null) {
      return false;
    }
    return runInTransaction(
        () -> {
          Post post =
              postPersistencePort
                  .loadPostForUpdate(command.postId())
                  .orElseThrow(PostNotFoundException::new);
          if (!post.matchesCurrentCreateExecutionIntent(executionIntentId)
              || !post.isPublicationPending()
              || !post.isOwnedBy(command.requesterId())
              || !matchesExpectedLocalState(command, preparation, post)) {
            return false;
          }
          QuestionPublicationEvidence evidence =
              loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), command.requesterId());
          return !evidence.projectionExists()
              && evidence.activeCreateIntentExists()
              && evidence.hasLatestCreateExecutionIntent(executionIntentId);
        });
  }

  private boolean matchesExpectedLocalState(
      RecoverQuestionPostEscrowCommand command, RecoveryPreparation preparation, Post post) {
    if (!preparation.matchesCurrentPayload(post)) {
      return false;
    }
    if (command.title() != null && !Objects.equals(command.title(), post.getTitle())) {
      return false;
    }
    if (command.tags() != null
        && !normalizeTags(command.tags())
            .equals(normalizeTags(loadTagPort.findTagNamesByPostId(post.getId())))) {
      return false;
    }
    return command.imageIds() == null || command.imageIds().equals(loadCurrentImageIds(post));
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    return tags.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .filter(name -> !name.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  private List<Long> loadCurrentImageIds(Post post) {
    return loadPostImagesPort.loadImages(post.getType(), post.getId()).slots().stream()
        .map(slot -> slot.imageId())
        .toList();
  }

  private void cancelPreparedIntentIfOwned(QuestionExecutionWriteView web3, String reason) {
    if (web3 == null || web3.existing() || web3.executionIntent() == null) {
      return;
    }
    questionLifecycleExecutionPort.cancelSignableIntent(web3.executionIntent().id(), reason);
  }

  private boolean hasPostAggregateEdit(RecoverQuestionPostEscrowCommand command) {
    return command.title() != null || command.content() != null || command.tags() != null;
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record RecoveryPayload(String questionContent, Long rewardMztk) {}

  private record RecoveryPreparation(
      String questionContent,
      Long rewardMztk,
      PostPublicationStatus expectedPublicationStatus,
      String expectedCurrentCreateExecutionIntentId,
      String expectedPublicationFailureTerminalStatus,
      String expectedPublicationFailureReason,
      long activeAnswerCount) {

    private static RecoveryPreparation from(
        Post expectedPost, Post payloadPost, long activeAnswerCount) {
      return new RecoveryPreparation(
          payloadPost.getContent(),
          payloadPost.getReward(),
          expectedPost.getPublicationStatus(),
          expectedPost.getCurrentCreateExecutionIntentId(),
          expectedPost.getPublicationFailureTerminalStatus(),
          expectedPost.getPublicationFailureReason(),
          activeAnswerCount);
    }

    private boolean matchesCurrentPayload(Post post) {
      return Objects.equals(questionContent, post.getContent())
          && Objects.equals(rewardMztk, post.getReward());
    }
  }
}
