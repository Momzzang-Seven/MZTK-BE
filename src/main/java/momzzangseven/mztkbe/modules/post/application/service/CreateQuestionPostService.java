package momzzangseven.mztkbe.modules.post.application.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostPublicationStateException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.in.CreateQuestionPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PublishPostDeletedEventPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates question-board posts while preserving the legacy XP response contract.
 *
 * <p>When Web3 escrow wiring is enabled, the service also performs precheck and returns the newly
 * prepared question lifecycle intent as nullable write payload.
 */
@Service
@RequiredArgsConstructor
public class CreateQuestionPostService implements CreateQuestionPostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  private final PublishPostDeletedEventPort publishPostDeletedEventPort;
  private final ApplicationEventPublisher eventPublisher;
  private final ZoneId appZoneId;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  /** Creates a question post and optionally prepares the initial escrow execution intent. */
  @Override
  public CreateQuestionPostResult execute(CreatePostCommand command) {
    validateQuestionCommand(command);
    command.validate();
    validatePostImagesIfPresent(command);
    questionLifecycleExecutionPort.precheckQuestionCreate(command.userId(), command.reward());

    boolean managesQuestionCreate = questionLifecycleExecutionPort.managesQuestionCreateLifecycle();
    Post savedPost = runInTransaction(() -> savePost(command, managesQuestionCreate));
    QuestionExecutionWriteView web3;
    try {
      web3 =
          questionLifecycleExecutionPort
              .prepareQuestionCreate(
                  savedPost.getId(),
                  savedPost.getUserId(),
                  savedPost.getContent(),
                  savedPost.getReward())
              .orElse(null);
    } catch (RuntimeException ex) {
      cleanupUnboundCreatedQuestionIfManaged(managesQuestionCreate, savedPost.getId());
      throw ex;
    }
    bindPreparedCreateIntentIfManaged(managesQuestionCreate, savedPost.getId(), web3);

    publishPostCreatedEvent(command.userId(), savedPost.getId(), savedPost.getType());

    // TODO: grantedXp is hard-coded temporarily. MOM-465 decoupled granting xp logic with business logics due to Hikari connection occupation problem.
    return new CreateQuestionPostResult(savedPost.getId(), false, 30L, "게시글 작성 완료", web3);
  }

  private void validateQuestionCommand(CreatePostCommand command) {
    if (command.type() != PostType.QUESTION) {
      throw new PostInvalidInputException("CreateQuestionPostService supports question posts only");
    }
  }

  private void validatePostImagesIfPresent(CreatePostCommand command) {
    if (command.imageIds() == null || command.imageIds().isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(
        command.userId(), null, command.type(), command.imageIds());
  }

  private Post savePost(CreatePostCommand command, boolean managesQuestionCreate) {
    // Question creation still uses the same persistence flow as free posts; only the outward
    // contract and escrow preparation differ.
    Post post =
        Post.create(
            command.userId(),
            command.type(),
            command.title(),
            command.content(),
            command.reward(),
            command.tags());
    if (managesQuestionCreate) {
      post = post.markPublicationPending();
    }

    Post savedPost = postPersistencePort.savePost(post);

    if (command.imageIds() != null && !command.imageIds().isEmpty()) {
      updatePostImagesPort.updateImages(
          savedPost.getUserId(), savedPost.getId(), savedPost.getType(), command.imageIds());
    }

    if (command.tags() != null && !command.tags().isEmpty()) {
      linkTagPort.linkTagsToPost(savedPost.getId(), command.tags());
    }

    return savedPost;
  }

  private void bindPreparedCreateIntentIfManaged(
      boolean managesQuestionCreate, Long postId, QuestionExecutionWriteView web3) {
    if (!managesQuestionCreate) {
      return;
    }
    String executionIntentId = preparedCreateIntentId(web3);
    if (executionIntentId == null) {
      cleanupUnboundCreatedQuestion(postId);
      throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
    }

    int updatedRows;
    try {
      updatedRows =
          runInTransaction(
              () ->
                  postPersistencePort.updateQuestionPublicationStateIfExpected(
                      postId,
                      PostPublicationStatus.PENDING,
                      null,
                      null,
                      null,
                      PostPublicationStatus.PENDING,
                      executionIntentId,
                      null,
                      null));
    } catch (RuntimeException ex) {
      if (isAlreadyBoundToCreateIntent(postId, executionIntentId)) {
        return;
      }
      cancelPreparedIntentIfOwned(web3, "question create intent bind failed");
      cleanupUnboundCreatedQuestion(postId);
      throw ex;
    }

    if (updatedRows > 0 || isAlreadyBoundToCreateIntent(postId, executionIntentId)) {
      return;
    }

    cancelPreparedIntentIfOwned(web3, "question create intent bind failed");
    cleanupUnboundCreatedQuestion(postId);
    throw new PostPublicationStateException(ErrorCode.QUESTION_PUBLICATION_STATE_CONFLICT);
  }

  /**
   * Publishes the post-created event in its own transaction so the {@code level} module's
   * AFTER_COMMIT handler fires. Publishing only after the full create-and-bind flow succeeds
   * preserves the legacy "XP only on successful question creation" behavior and avoids granting XP
   * for a question that is subsequently cleaned up.
   */
  private void publishPostCreatedEvent(Long userId, Long postId, PostType type) {
    runInTransaction(
        () -> {
          eventPublisher.publishEvent(
              new PostCreatedEvent(userId, postId, type, LocalDateTime.now(appZoneId)));
          return null;
        });
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
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

  private boolean isAlreadyBoundToCreateIntent(Long postId, String executionIntentId) {
    return runInTransaction(
        () ->
            postPersistencePort
                .loadPostForUpdate(postId)
                .filter(post -> post.matchesCurrentCreateExecutionIntent(executionIntentId))
                .isPresent());
  }

  private void cancelPreparedIntentIfOwned(QuestionExecutionWriteView web3, String reason) {
    if (web3 == null || web3.existing() || web3.executionIntent() == null) {
      return;
    }
    questionLifecycleExecutionPort.cancelSignableIntent(web3.executionIntent().id(), reason);
  }

  private void cleanupUnboundCreatedQuestionIfManaged(boolean managesQuestionCreate, Long postId) {
    if (managesQuestionCreate) {
      cleanupUnboundCreatedQuestion(postId);
    }
  }

  private void cleanupUnboundCreatedQuestion(Long postId) {
    runInTransaction(
        () -> {
          postPersistencePort
              .loadPostForUpdate(postId)
              .filter(post -> post.getType() == PostType.QUESTION)
              .filter(Post::isPublicationPending)
              .filter(post -> post.getCurrentCreateExecutionIntentId() == null)
              .ifPresent(
                  post -> {
                    postPersistencePort.deletePost(post);
                    publishPostDeletedEventPort.publish(
                        new PostDeletedEvent(post.getId(), post.getType()));
                  });
          return null;
        });
  }
}
