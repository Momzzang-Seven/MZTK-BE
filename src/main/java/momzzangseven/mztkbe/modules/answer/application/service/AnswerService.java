package momzzangseven.mztkbe.modules.answer.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.CountAnswersUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswersByPostUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerIdsByPostUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetVisibleAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetVisibleAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswerCommentsPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerExecutionResumePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerLifecycleAction;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coordinates answer CRUD plus owner-scoped Web3 escrow read/write wiring.
 *
 * <p>Write responses expose nullable Web3 payloads only when a mutation produced new on-chain work.
 * Read responses expose resumable execution summaries only for the caller's own answers.
 */
@Service
@RequiredArgsConstructor
public class AnswerService
    implements CountAnswersUseCase,
        CreateAnswerUseCase,
        GetAnswerUseCase,
        GetAnswerIdsByPostUseCase,
        GetAnswerSummaryUseCase,
        GetAnswerSummaryForUpdateUseCase,
        UpdateAnswerUseCase,
        DeleteAnswerUseCase,
        DeleteAnswersByPostUseCase,
        MarkAnswerAcceptedUseCase,
        GetVisibleAnswerSummaryUseCase,
        GetVisibleAnswerSummaryForUpdateUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final CountAnswersPort countAnswersPort;
  private final CountAnswerCommentsPort countAnswerCommentsPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final LoadAnswerWriterPort loadAnswerWriterPort;
  private final LoadAnswerImagesPort loadAnswerImagesPort;
  private final LoadAnswerLikePort loadAnswerLikePort;
  private final UpdateAnswerImagesPort updateAnswerImagesPort;
  private final AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  private final AnswerUpdateImagePort answerUpdateImagePort;
  private final AnswerUpdateStatePort answerUpdateStatePort;
  private final LoadAnswerExecutionResumePort loadAnswerExecutionResumePort;
  private final AnswerReadAssembler answerReadAssembler;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  /** Returns the current persisted active answer count for the given question post. */
  @Override
  @Transactional(readOnly = true)
  public long countAnswers(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return countAnswersPort.countAnswers(postId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countPublicVisibleAnswers(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return countAnswersPort.countPublicVisibleAnswers(postId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countOnchainBlockingAnswers(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return countAnswersPort.countOnchainBlockingAnswers(postId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsPreparingOrPendingCreateByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return loadAnswerPort.existsPreparingOrPendingCreateByPostId(postId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, Long> countAnswersByPostIds(List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Map.of();
    }
    return countAnswersPort.countAnswersByPostIds(postIds);
  }

  /** Creates a new answer for a question post. */
  @Override
  public CreateAnswerResult execute(CreateAnswerCommand command) {
    command.validate();

    LoadPostPort.PostContext post = loadPost(command.postId());
    validateAnswerablePost(post);
    validatePostWritable(post);
    answerLifecycleExecutionPort.precheckAnswerCreate(post.postId(), post.content());

    Answer answer =
        Answer.create(
            post.postId(),
            post.writerId(),
            post.answerLocked(),
            command.userId(),
            command.content());
    boolean managedCreate =
        answerLifecycleExecutionPort.managesAnswerLifecycle(AnswerLifecycleAction.CREATE);
    String createPreparationToken = managedCreate ? UUID.randomUUID().toString() : null;
    if (managedCreate) {
      answer = answer.reserveCreate(createPreparationToken, LocalDateTime.now().plusMinutes(15));
    }

    Answer savedAnswer = saveAnswerPort.saveAnswer(answer);

    try {
      if (command.imageIds() != null && !command.imageIds().isEmpty()) {
        updateAnswerImagesPort.updateImages(
            savedAnswer.getUserId(), savedAnswer.getId(), command.imageIds());
      }
    } catch (RuntimeException ex) {
      cleanupUnboundCreatedAnswer(savedAnswer.getId());
      throw ex;
    }

    int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(savedAnswer.getPostId()));
    AnswerExecutionWriteView web3;
    try {
      web3 =
          answerLifecycleExecutionPort
              .prepareAnswerCreate(
                  savedAnswer.getPostId(),
                  savedAnswer.getId(),
                  savedAnswer.getUserId(),
                  post.writerId(),
                  post.content(),
                  post.reward(),
                  savedAnswer.getContent(),
                  activeAnswerCount)
              .orElse(null);
    } catch (RuntimeException ex) {
      if (managedCreate) {
        cleanupUnboundCreatedAnswer(savedAnswer.getId());
      }
      throw ex;
    }
    if (managedCreate && web3 != null) {
      int bound =
          runInTransaction(
              () ->
                  saveAnswerPort.bindCreateIntentIfCurrent(
                      savedAnswer.getId(), createPreparationToken, web3.executionIntent().id()));
      if (bound == 0) {
        cancelPreparedIntent(web3, "answer create intent bind failed");
        throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
      }
    }

    return new CreateAnswerResult(
        savedAnswer.getPostId(), savedAnswer.getId(), savedAnswer.getPublicationStatus(), web3);
  }

  /** Loads answers for a question post together with writer summary fields used by the API. */
  @Override
  @Transactional(readOnly = true)
  public List<AnswerResult> execute(Long postId, Long currentUserId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    LoadPostPort.PostContext post = loadPost(postId);
    validatePostReadable(post, currentUserId);
    validateAnswerablePost(post);

    List<Answer> answers =
        loadAnswerPort.loadPublicAndOwnerVisibleAnswersByPostId(postId, currentUserId);
    Map<Long, LoadAnswerWriterPort.WriterSummary> writers =
        answers.isEmpty()
            ? Map.of()
            : loadAnswerWriterPort.loadWritersByIds(
                answers.stream().map(Answer::getUserId).distinct().toList());
    Map<Long, AnswerImageResult> imagesByAnswerId =
        answers.isEmpty()
            ? Map.of()
            : loadAnswerImagesPort.loadImagesByAnswerIds(
                answers.stream().map(Answer::getId).toList());
    List<Long> answerIds = answers.stream().map(Answer::getId).toList();
    Map<Long, Long> likeCounts =
        answers.isEmpty() ? Map.of() : loadAnswerLikePort.countLikeByAnswerIds(answerIds);
    Map<Long, Long> loadedCommentCounts =
        answers.isEmpty() ? Map.of() : countAnswerCommentsPort.countCommentsByAnswerIds(answerIds);
    Map<Long, Long> commentCounts = loadedCommentCounts == null ? Map.of() : loadedCommentCounts;
    Set<Long> likedAnswerIds =
        answers.isEmpty()
            ? Set.of()
            : loadAnswerLikePort.loadLikedAnswerIds(answerIds, currentUserId);
    List<Long> ownerAnswerIds =
        currentUserId == null
            ? List.of()
            : answers.stream()
                .filter(answer -> currentUserId.equals(answer.getUserId()))
                .map(Answer::getId)
                .toList();
    // Owner-visible resume rows are hydrated in one batch to avoid one latest-summary lookup per
    // owned answer.
    Map<Long, momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView>
        web3ExecutionsByAnswerId =
            ownerAnswerIds.isEmpty()
                ? Map.of()
                : loadAnswerExecutionResumePort.loadLatestByAnswerIds(ownerAnswerIds);

    return answers.stream()
        .map(
            answer ->
                toResult(
                    answer,
                    writers,
                    imagesByAnswerId,
                    likeCounts,
                    commentCounts,
                    likedAnswerIds,
                    web3ExecutionsByAnswerId))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<GetAnswerSummaryUseCase.AnswerSummary> getAnswerSummary(Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    return loadAnswerPort.loadAnswer(answerId).map(this::toAnswerSummary);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<GetAnswerSummaryUseCase.AnswerSummary> getAnswerSummaryForUpdate(Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    return loadAnswerPort.loadAnswerForUpdate(answerId).map(this::toAnswerSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<GetAnswerSummaryUseCase.AnswerSummary> getVisibleAnswerSummary(Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    return loadAnswerPort
        .loadAnswer(answerId)
        .filter(this::isVisibleForFollowUpMutation)
        .map(this::toAnswerSummary);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<GetAnswerSummaryUseCase.AnswerSummary> getVisibleAnswerSummaryForUpdate(
      Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    return loadAnswerPort
        .loadAnswerForUpdate(answerId)
        .filter(this::isVisibleForFollowUpMutation)
        .map(this::toAnswerSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> getAnswerIdsByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return loadAnswerPort.loadAnswerIdsByPostId(postId);
  }

  /** Updates mutable answer fields. Omitted fields are preserved. */
  @Override
  public AnswerMutationResult execute(UpdateAnswerCommand command) {
    command.validate();

    UpdatePreparation preparation = runInTransaction(() -> prepareLocalUpdate(command));
    if (!preparation.shouldPrepareWeb3()) {
      return preparation.localResult();
    }

    Optional<momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView>
        preparedWeb3;
    try {
      if (preparation.managedUpdate()) {
        preparedWeb3 =
            answerLifecycleExecutionPort.prepareAnswerUpdate(
                preparation.postId(),
                preparation.answerId(),
                preparation.userId(),
                preparation.questionWriterId(),
                preparation.questionContent(),
                preparation.reward(),
                preparation.answerContent(),
                preparation.activeAnswerCount(),
                preparation.updateVersion(),
                preparation.updateToken());
      } else {
        preparedWeb3 =
            preparation.contentChanged()
                ? answerLifecycleExecutionPort.prepareAnswerUpdate(
                    preparation.postId(),
                    preparation.answerId(),
                    preparation.userId(),
                    preparation.questionWriterId(),
                    preparation.questionContent(),
                    preparation.reward(),
                    preparation.answerContent(),
                    preparation.activeAnswerCount())
                : answerLifecycleExecutionPort.recoverAnswerUpdate(
                    preparation.postId(),
                    preparation.answerId(),
                    preparation.userId(),
                    preparation.questionWriterId(),
                    preparation.questionContent(),
                    preparation.reward(),
                    preparation.answerContent(),
                    preparation.activeAnswerCount());
      }
    } catch (RuntimeException ex) {
      if (preparation.managedUpdate()) {
        markAnswerUpdatePreparationFailed(preparation, ex);
      }
      throw ex;
    }
    if (preparation.managedUpdate() && preparedWeb3.isPresent()) {
      int bound =
          runInTransaction(
              () ->
                  answerUpdateStatePort.bindIntentIfCurrent(
                      preparation.answerId(),
                      preparation.updateVersion(),
                      preparation.updateToken(),
                      preparation.preparationToken(),
                      preparedWeb3.orElseThrow().executionIntent().id()));
      if (bound == 0) {
        cancelPreparedIntent(preparedWeb3.orElseThrow(), "answer update intent bind failed");
        throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
      }
    }
    return new AnswerMutationResult(
        preparation.postId(),
        preparation.answerId(),
        preparation.publicationStatus(),
        preparation.managedUpdate() && preparedWeb3.isPresent()
            ? AnswerUpdateStatus.INTENT_BOUND
            : preparation.pendingUpdateStatus(),
        preparation.updateVersion(),
        preparedWeb3.orElse(null));
  }

  private UpdatePreparation prepareLocalUpdate(UpdateAnswerCommand command) {
    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());
    validateVisibleForMutation(answer);
    if (answerUpdateStatePort.hasBlockingUpdate(answer.getId())) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_UPDATE_ONCHAIN_IN_PROGRESS);
    }
    LoadPostPort.PostContext post = loadPost(answer.getPostId());
    validatePostWritable(post);
    // Only content changes affect the escrow payload hash. Image-only updates stay local and
    // therefore return web3 = null.
    boolean contentChanged =
        command.content() != null && !command.content().equals(answer.getContent());
    boolean managedUpdate =
        contentChanged
            && answerLifecycleExecutionPort.managesAnswerLifecycle(AnswerLifecycleAction.UPDATE);

    Answer updatedAnswer = answer.update(command.content(), command.userId(), post.answerLocked());
    AnswerUpdateStatePort.AnswerUpdateState updateState = null;
    String preparationToken = null;
    if (managedUpdate) {
      preparationToken = UUID.randomUUID().toString();
      updateState =
          answerUpdateStatePort.createPreparing(
              answer.getId(),
              updatedAnswer.getContent(),
              preparationToken,
              LocalDateTime.now().plusMinutes(15));
    }
    if (command.content() == null
        && answerLifecycleExecutionPort.hasActiveAnswerIntent(answer.getId())) {
      throw new AnswerInvalidInputException(
          "Answer has pending onchain mutation; wait for completion or recover first.");
    }
    if (!managedUpdate && updatedAnswer != answer) {
      saveAnswerPort.saveAnswer(updatedAnswer);
    }

    if (command.imageIds() != null && managedUpdate) {
      answerUpdateImagePort.savePendingImages(
          updateState.id(), command.userId(), command.answerId(), command.imageIds());
    } else if (command.imageIds() != null) {
      updateAnswerImagesPort.updateImages(command.userId(), command.answerId(), command.imageIds());
    }

    if (command.content() == null) {
      return UpdatePreparation.localOnly(
          new AnswerMutationResult(
              answer.getPostId(), answer.getId(), updatedAnswer.getPublicationStatus(), null));
    }

    return UpdatePreparation.web3(
        answer.getPostId(),
        answer.getId(),
        command.userId(),
        post.writerId(),
        post.content(),
        post.reward(),
        updatedAnswer.getContent(),
        Math.toIntExact(countAnswersPort.countAnswers(answer.getPostId())),
        contentChanged,
        managedUpdate,
        updateState == null ? null : updateState.updateVersion(),
        updateState == null ? null : updateState.updateToken(),
        managedUpdate ? AnswerUpdateStatus.PREPARING : null,
        preparationToken,
        answer.getPublicationStatus());
  }

  /** Deletes a single answer requested by its owner. */
  @Override
  public AnswerMutationResult execute(DeleteAnswerCommand command) {
    command.validate();

    DeletePreparation preparation = runInTransaction(() -> prepareLocalDelete(command));
    if (!preparation.shouldPrepareWeb3()) {
      return preparation.localResult();
    }
    Optional<AnswerExecutionWriteView> web3;
    try {
      web3 =
          answerLifecycleExecutionPort.prepareAnswerDelete(
              preparation.postId(),
              preparation.answerId(),
              preparation.userId(),
              preparation.questionWriterId(),
              preparation.questionContent(),
              preparation.reward(),
              preparation.activeAnswerCount());
    } catch (RuntimeException ex) {
      if (preparation.managedDelete()) {
        rollbackAnswerDeletePreparation(preparation, ex);
      }
      throw ex;
    }
    if (web3.isPresent()) {
      if (preparation.managedDelete()) {
        int bound =
            runInTransaction(
                () ->
                    saveAnswerPort.bindDeleteIntentIfCurrent(
                        preparation.answerId(),
                        preparation.preparationToken(),
                        web3.orElseThrow().executionIntent().id()));
        if (bound == 0) {
          cancelPreparedIntent(web3.get(), "answer delete intent bind failed");
          throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
        }
      }
      return new AnswerMutationResult(
          preparation.postId(),
          preparation.answerId(),
          preparation.publicationStatus(),
          web3.get());
    }
    return runInTransaction(
        () -> {
          deleteAnswerPort.deleteAnswer(preparation.answerId());
          publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(preparation.answerId()));
          return new AnswerMutationResult(
              preparation.postId(), preparation.answerId(), preparation.publicationStatus(), null);
        });
  }

  private DeletePreparation prepareLocalDelete(DeleteAnswerCommand command) {
    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());
    validateVisibleForMutation(answer);
    if (answerUpdateStatePort.hasBlockingUpdate(answer.getId())) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_UPDATE_ONCHAIN_IN_PROGRESS);
    }
    LoadPostPort.PostContext post = loadPost(answer.getPostId());
    validatePostWritable(post);

    answer.validateDeletable(command.userId(), post.answerLocked());
    boolean managedDelete =
        answerLifecycleExecutionPort.managesAnswerLifecycle(AnswerLifecycleAction.DELETE);
    String deletePreparationToken = managedDelete ? UUID.randomUUID().toString() : null;
    if (managedDelete) {
      answer =
          saveAnswerPort.saveAnswer(
              answer.beginDelete(deletePreparationToken, LocalDateTime.now().plusMinutes(15)));
    }
    int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(answer.getPostId()));
    return DeletePreparation.web3(
        answer.getPostId(),
        answer.getId(),
        command.userId(),
        post.writerId(),
        post.content(),
        post.reward(),
        activeAnswerCount,
        managedDelete,
        deletePreparationToken,
        answer.getPublicationStatus());
  }

  /** Deletes all answers belonging to a post after an internal post-deleted event. */
  @Override
  @Transactional
  public void deleteByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    List<Long> answerIds = loadAnswerPort.loadAnswerIdsByPostId(postId);
    deleteByPostId(postId, answerIds);
  }

  @Override
  @Transactional
  public void deleteByPostId(Long postId, List<Long> answerIds) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (answerIds == null || answerIds.isEmpty()) {
      deleteAnswerPort.deleteAnswersByPostId(postId);
      return;
    }
    deleteAnswerPort.deleteAnswersByPostId(postId);
    answerIds.forEach(
        answerId -> publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId)));
  }

  @Override
  @Transactional
  public void markAccepted(Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }

    Answer answer = loadAnswerForUpdate(answerId);
    Answer acceptedAnswer = answer.accept();
    if (acceptedAnswer != answer) {
      saveAnswerPort.saveAnswer(acceptedAnswer);
    }
  }

  private LoadPostPort.PostContext loadPost(Long postId) {
    return loadPostPort.loadPost(postId).orElseThrow(AnswerPostNotFoundException::new);
  }

  private Answer loadAnswer(Long answerId) {
    return loadAnswerPort.loadAnswer(answerId).orElseThrow(AnswerNotFoundException::new);
  }

  private Answer loadAnswerForUpdate(Long answerId) {
    return loadAnswerPort.loadAnswerForUpdate(answerId).orElseThrow(AnswerNotFoundException::new);
  }

  private AnswerResult toResult(
      Answer answer,
      Map<Long, LoadAnswerWriterPort.WriterSummary> writers,
      Map<Long, AnswerImageResult> imagesByAnswerId,
      Map<Long, Long> likeCounts,
      Map<Long, Long> commentCounts,
      Set<Long> likedAnswerIds,
      Map<Long, momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView>
          web3ExecutionsByAnswerId) {
    return answerReadAssembler.assemble(
        answer,
        writers.get(answer.getUserId()),
        imagesByAnswerId.get(answer.getId()),
        likeCounts.getOrDefault(answer.getId(), 0L),
        commentCounts.getOrDefault(answer.getId(), 0L),
        likedAnswerIds.contains(answer.getId()),
        web3ExecutionsByAnswerId.get(answer.getId()));
  }

  private void validateVisibleForMutation(Answer answer) {
    if (answer.getPublicationStatus() == AnswerPublicationStatus.PENDING) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_PENDING);
    }
    if (answer.getPublicationStatus() == AnswerPublicationStatus.FAILED) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_CREATE_RECOVERY_REQUIRED);
    }
    if (!answer.isPubliclyVisible()) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
    }
  }

  private boolean isVisibleForFollowUpMutation(Answer answer) {
    return answer.isPubliclyVisible() && !answerUpdateStatePort.hasBlockingUpdate(answer.getId());
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private void cancelPreparedIntent(AnswerExecutionWriteView web3, String reason) {
    if (web3 == null || web3.executionIntent() == null) {
      return;
    }
    answerLifecycleExecutionPort.cancelSignableIntent(web3.executionIntent().id(), reason);
  }

  private void cleanupUnboundCreatedAnswer(Long answerId) {
    runInTransaction(
        () -> {
          deleteAnswerPort.deleteAnswer(answerId);
          publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId));
          return null;
        });
  }

  private void markAnswerUpdatePreparationFailed(
      UpdatePreparation preparation, RuntimeException ex) {
    runInTransaction(
        () -> {
          answerUpdateStatePort.markPreparationFailedIfCurrent(
              preparation.answerId(),
              preparation.updateVersion(),
              preparation.updateToken(),
              preparation.preparationToken(),
              ex.getMessage());
          return null;
        });
  }

  private void rollbackAnswerDeletePreparation(DeletePreparation preparation, RuntimeException ex) {
    runInTransaction(
        () -> {
          saveAnswerPort.rollbackDeletePreparationIfCurrent(
              preparation.answerId(),
              preparation.preparationToken(),
              "PREPARATION_FAILED",
              ex.getMessage());
          return null;
        });
  }

  private record UpdatePreparation(
      boolean shouldPrepareWeb3,
      AnswerMutationResult localResult,
      Long postId,
      Long answerId,
      Long userId,
      Long questionWriterId,
      String questionContent,
      Long reward,
      String answerContent,
      int activeAnswerCount,
      boolean contentChanged,
      boolean managedUpdate,
      Long updateVersion,
      String updateToken,
      AnswerUpdateStatus pendingUpdateStatus,
      String preparationToken,
      AnswerPublicationStatus publicationStatus) {

    static UpdatePreparation localOnly(AnswerMutationResult localResult) {
      return new UpdatePreparation(
          false,
          localResult,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          0,
          false,
          false,
          null,
          null,
          null,
          null,
          null);
    }

    static UpdatePreparation web3(
        Long postId,
        Long answerId,
        Long userId,
        Long questionWriterId,
        String questionContent,
        Long reward,
        String answerContent,
        int activeAnswerCount,
        boolean contentChanged,
        boolean managedUpdate,
        Long updateVersion,
        String updateToken,
        AnswerUpdateStatus pendingUpdateStatus,
        String preparationToken,
        AnswerPublicationStatus publicationStatus) {
      return new UpdatePreparation(
          true,
          null,
          postId,
          answerId,
          userId,
          questionWriterId,
          questionContent,
          reward,
          answerContent,
          activeAnswerCount,
          contentChanged,
          managedUpdate,
          updateVersion,
          updateToken,
          pendingUpdateStatus,
          preparationToken,
          publicationStatus);
    }
  }

  private record DeletePreparation(
      boolean shouldPrepareWeb3,
      AnswerMutationResult localResult,
      Long postId,
      Long answerId,
      Long userId,
      Long questionWriterId,
      String questionContent,
      Long reward,
      int activeAnswerCount,
      boolean managedDelete,
      String preparationToken,
      AnswerPublicationStatus publicationStatus) {

    static DeletePreparation web3(
        Long postId,
        Long answerId,
        Long userId,
        Long questionWriterId,
        String questionContent,
        Long reward,
        int activeAnswerCount,
        boolean managedDelete,
        String preparationToken,
        AnswerPublicationStatus publicationStatus) {
      return new DeletePreparation(
          true,
          null,
          postId,
          answerId,
          userId,
          questionWriterId,
          questionContent,
          reward,
          activeAnswerCount,
          managedDelete,
          preparationToken,
          publicationStatus);
    }
  }

  private void validateAnswerBelongsToPost(Answer answer, Long postId) {
    if (!answer.getPostId().equals(postId)) {
      throw new AnswerPostMismatchException();
    }
  }

  private void validateAnswerablePost(LoadPostPort.PostContext post) {
    if (!isQuestionPost(post)) {
      throw new AnswerUnsupportedPostTypeException();
    }
  }

  private void validatePostReadable(LoadPostPort.PostContext post, Long requesterUserId) {
    if (!post.readableBy(requesterUserId)) {
      throw new AnswerPostNotFoundException();
    }
  }

  private void validatePostWritable(LoadPostPort.PostContext post) {
    if (!post.writable()) {
      throw new AnswerInvalidInputException(
          "Post is not in a state that allows answer interactions.");
    }
  }

  private boolean isQuestionPost(LoadPostPort.PostContext post) {
    return post.questionPost();
  }

  private GetAnswerSummaryUseCase.AnswerSummary toAnswerSummary(Answer answer) {
    return new GetAnswerSummaryUseCase.AnswerSummary(
        answer.getId(),
        answer.getPostId(),
        answer.getUserId(),
        answer.getContent(),
        Boolean.TRUE.equals(answer.getIsAccepted()));
  }
}
