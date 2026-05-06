package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.global.error.post.PostPublicationStateException;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles post update/delete mutations for both free and question boards.
 *
 * <p>Only question mutations that change the on-chain question content prepare new Web3 work.
 * Free-board mutations and metadata-only question edits intentionally return {@code web3 = null}.
 */
@Service
@RequiredArgsConstructor
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final CountAnswersPort countAnswersPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  private final LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;
  private final PostVisibilityPolicy postVisibilityPolicy;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  /**
   * Updates a post and prepares question escrow work only when question content actually changed.
   */
  @Override
  public PostMutationResult updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    command.validate();

    UpdatePreparation preparation =
        runInTransaction(() -> prepareLocalUpdate(currentUserId, postId, command));
    QuestionExecutionWriteView web3 = prepareQuestionUpdateExecution(preparation);
    return new PostMutationResult(postId, web3);
  }

  private UpdatePreparation prepareLocalUpdate(
      Long currentUserId, Long postId, UpdatePostCommand command) {
    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    postVisibilityPolicy.validateOwnerMutationAllowed(post);
    validateQuestionUpdatePublicationAllowed(post);
    long activeAnswerCount = countActiveAnswers(post);
    validatePostImagesIfPresent(currentUserId, postId, post.getType(), command.imageIds());

    boolean contentChanged =
        command.content() != null && !command.content().equals(post.getContent());
    boolean failedQuestion = isFailedQuestion(post);

    Post updatedPost =
        post.update(command.title(), command.content(), command.tags(), activeAnswerCount);
    boolean shouldPrepareQuestionUpdate =
        PostType.QUESTION.equals(post.getType()) && command.content() != null && !failedQuestion;
    if (PostType.QUESTION.equals(post.getType())
        && !shouldPrepareQuestionUpdate
        && questionLifecycleExecutionPort.hasActiveQuestionIntent(postId)) {
      throw new PostInvalidInputException(
          "Question has pending onchain mutation; wait for completion or recover first.");
    }
    postPersistencePort.savePost(updatedPost);

    if (command.tags() != null) {
      linkTagPort.updateTags(postId, command.tags());
    }

    // Delegate image sync to the output port; run only when imageIds is explicitly provided.
    if (command.imageIds() != null) {
      updatePostImagesPort.updateImages(currentUserId, postId, post.getType(), command.imageIds());
    }

    return new UpdatePreparation(
        postId,
        currentUserId,
        updatedPost.getContent(),
        updatedPost.getReward(),
        shouldPrepareQuestionUpdate,
        contentChanged);
  }

  private QuestionExecutionWriteView prepareQuestionUpdateExecution(UpdatePreparation preparation) {
    if (!preparation.shouldPrepareQuestionUpdate()) {
      return null;
    }
    return (preparation.contentChanged()
            ? questionLifecycleExecutionPort.prepareQuestionUpdate(
                preparation.postId(),
                preparation.currentUserId(),
                preparation.questionContent(),
                preparation.rewardMztk())
            : questionLifecycleExecutionPort.recoverQuestionUpdate(
                preparation.postId(),
                preparation.currentUserId(),
                preparation.questionContent(),
                preparation.rewardMztk()))
        .orElse(null);
  }

  /**
   * Deletes a post and, for question posts, defers local removal until on-chain delete confirms.
   */
  @Override
  public PostMutationResult deletePost(Long currentUserId, Long postId) {
    DeletePreparation preparation =
        runInTransaction(() -> prepareLocalDelete(currentUserId, postId));
    if (!preparation.shouldPrepareQuestionDelete()) {
      return new PostMutationResult(postId, null);
    }

    QuestionExecutionWriteView web3 =
        questionLifecycleExecutionPort
            .prepareQuestionDelete(
                postId, currentUserId, preparation.questionContent(), preparation.rewardMztk())
            .orElse(null);
    if (web3 != null) {
      return new PostMutationResult(postId, web3);
    }

    runInTransaction(
        () -> {
          deletePostLocally(currentUserId, postId);
          return null;
        });
    return new PostMutationResult(postId, null);
  }

  private DeletePreparation prepareLocalDelete(Long currentUserId, Long postId) {
    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    postVisibilityPolicy.validateOwnerMutationAllowed(post);
    validateQuestionDeletePublicationAllowed(post, currentUserId);
    post.validateDeletable(countActiveAnswers(post));

    if (isFailedQuestion(post)) {
      postPersistencePort.deletePost(post);
      eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
      return DeletePreparation.completed();
    }

    if (PostType.QUESTION.equals(post.getType())) {
      return DeletePreparation.question(post.getContent(), post.getReward());
    }
    postPersistencePort.deletePost(post);
    eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
    return DeletePreparation.completed();
  }

  private void deletePostLocally(Long currentUserId, Long postId) {
    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    postVisibilityPolicy.validateOwnerMutationAllowed(post);
    validateQuestionDeletePublicationAllowed(post, currentUserId);
    post.validateDeletable(countActiveAnswers(post));
    postPersistencePort.deletePost(post);
    eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
  }

  private Post loadPostOrThrow(Long postId) {
    return postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);
  }

  private long countActiveAnswers(Post post) {
    if (!PostType.QUESTION.equals(post.getType())) {
      return 0L;
    }
    return countAnswersPort.countAnswers(post.getId());
  }

  private void validateQuestionUpdatePublicationAllowed(Post post) {
    if (!PostType.QUESTION.equals(post.getType())) {
      return;
    }
    if (post.isPublicationPending()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }
    if (post.isPublicationFailed()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_CREATE_RECOVERY_REQUIRED);
    }
  }

  private void validateQuestionDeletePublicationAllowed(Post post, Long requesterUserId) {
    if (!PostType.QUESTION.equals(post.getType())) {
      return;
    }
    if (post.isPublicationPending()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }
    if (!post.isPublicationFailed()) {
      return;
    }
    if (!questionLifecycleExecutionPort.managesQuestionCreateLifecycle()) {
      return;
    }

    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), requesterUserId);
    if (evidence.projectionExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }
    if (evidence.activeCreateIntentExists()) {
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_PENDING);
    }
  }

  private boolean isFailedQuestion(Post post) {
    return PostType.QUESTION.equals(post.getType()) && post.isPublicationFailed();
  }

  private void validatePostImagesIfPresent(
      Long userId, Long postId, PostType postType, List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(userId, postId, postType, imageIds);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record UpdatePreparation(
      Long postId,
      Long currentUserId,
      String questionContent,
      Long rewardMztk,
      boolean shouldPrepareQuestionUpdate,
      boolean contentChanged) {}

  private record DeletePreparation(
      boolean shouldPrepareQuestionDelete, String questionContent, Long rewardMztk) {

    private static DeletePreparation completed() {
      return new DeletePreparation(false, null, null);
    }

    private static DeletePreparation question(String questionContent, Long rewardMztk) {
      return new DeletePreparation(true, questionContent, rewardMztk);
    }
  }
}
