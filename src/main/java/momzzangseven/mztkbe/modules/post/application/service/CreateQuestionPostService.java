package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreateQuestionPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
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
 * Creates question-board posts while preserving the legacy XP response contract.
 *
 * <p>When Web3 escrow wiring is enabled, the service also performs precheck and returns the newly
 * prepared question lifecycle intent as nullable write payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateQuestionPostService implements CreateQuestionPostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final PostXpService postXpService;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
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
    QuestionExecutionWriteView web3 =
        questionLifecycleExecutionPort
            .prepareQuestionCreate(
                savedPost.getId(),
                savedPost.getUserId(),
                savedPost.getContent(),
                savedPost.getReward())
            .orElse(null);
    recordPreparedCreateIntentIfPresent(managesQuestionCreate, savedPost.getId(), web3);

    XpGrantResult xpResult = grantCreateXp(command.userId(), savedPost.getId());

    return new CreateQuestionPostResult(
        savedPost.getId(), xpResult.isXpGranted(), xpResult.grantedXp(), xpResult.message(), web3);
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

  private void recordPreparedCreateIntentIfPresent(
      boolean managesQuestionCreate, Long postId, QuestionExecutionWriteView web3) {
    if (!managesQuestionCreate
        || web3 == null
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

  private XpGrantResult grantCreateXp(Long userId, Long postId) {
    Long grantedXp = 0L;
    boolean isXpGranted = false;

    try {
      grantedXp = postXpService.grantCreatePostXp(userId, postId);
      if (grantedXp > 0) {
        isXpGranted = true;
      }
    } catch (Exception e) {
      log.warn("Post created but XP grant failed for user: {}", userId, e);
    }

    String message = isXpGranted ? "게시글 작성 완료! (+" + grantedXp + " XP)" : "게시글 작성 완료";
    return new XpGrantResult(isXpGranted, grantedXp, message);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record XpGrantResult(boolean isXpGranted, Long grantedXp, String message) {}
}
