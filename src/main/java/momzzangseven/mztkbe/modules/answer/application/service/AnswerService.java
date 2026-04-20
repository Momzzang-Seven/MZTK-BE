package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
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
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerExecutionResumePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        MarkAnswerAcceptedUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final CountAnswersPort countAnswersPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final LoadAnswerWriterPort loadAnswerWriterPort;
  private final LoadAnswerImagesPort loadAnswerImagesPort;
  private final LoadAnswerLikePort loadAnswerLikePort;
  private final UpdateAnswerImagesPort updateAnswerImagesPort;
  private final AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  private final LoadAnswerExecutionResumePort loadAnswerExecutionResumePort;
  private final AnswerReadAssembler answerReadAssembler;
  private final ApplicationEventPublisher eventPublisher;

  /** Returns the current persisted active answer count for the given question post. */
  @Override
  @Transactional(readOnly = true)
  public long countAnswers(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return countAnswersPort.countAnswers(postId);
  }

  /** Creates a new answer for a question post. */
  @Override
  @Transactional
  public CreateAnswerResult execute(CreateAnswerCommand command) {
    command.validate();

    LoadPostPort.PostContext post = loadPost(command.postId());
    validateAnswerablePost(post);
    answerLifecycleExecutionPort.precheckAnswerCreate(post.postId(), post.content());

    Answer answer =
        Answer.create(
            post.postId(),
            post.writerId(),
            post.answerLocked(),
            command.userId(),
            command.content());

    Answer savedAnswer = saveAnswerPort.saveAnswer(answer);

    if (command.imageIds() != null && !command.imageIds().isEmpty()) {
      updateAnswerImagesPort.updateImages(
          savedAnswer.getUserId(), savedAnswer.getId(), command.imageIds());
    }

    int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(savedAnswer.getPostId()));
    var web3 =
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

    return new CreateAnswerResult(savedAnswer.getPostId(), savedAnswer.getId(), web3);
  }

  /** Loads answers for a question post together with writer summary fields used by the API. */
  @Override
  @Transactional(readOnly = true)
  public List<AnswerResult> execute(Long postId, Long currentUserId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    LoadPostPort.PostContext post = loadPost(postId);
    validateAnswerablePost(post);

    List<Answer> answers = loadAnswerPort.loadAnswersByPostId(postId);
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
  public List<Long> getAnswerIdsByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    return loadAnswerPort.loadAnswerIdsByPostId(postId);
  }

  /** Updates mutable answer fields. Omitted fields are preserved. */
  @Override
  @Transactional
  public AnswerMutationResult execute(UpdateAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());
    LoadPostPort.PostContext post = loadPost(answer.getPostId());
    // Only content changes affect the escrow payload hash. Image-only updates stay local and
    // therefore return web3 = null.
    boolean contentChanged =
        command.content() != null && !command.content().equals(answer.getContent());

    Answer updatedAnswer = answer.update(command.content(), command.userId(), post.answerLocked());
    AnswerMutationResult preparedResult = null;
    if (command.content() != null) {
      int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(answer.getPostId()));
      var web3 =
          contentChanged
              ? answerLifecycleExecutionPort.prepareAnswerUpdate(
                  answer.getPostId(),
                  answer.getId(),
                  command.userId(),
                  post.writerId(),
                  post.content(),
                  post.reward(),
                  updatedAnswer.getContent(),
                  activeAnswerCount)
              : answerLifecycleExecutionPort.recoverAnswerUpdate(
                  answer.getPostId(),
                  answer.getId(),
                  command.userId(),
                  post.writerId(),
                  post.content(),
                  post.reward(),
                  updatedAnswer.getContent(),
                  activeAnswerCount);
      preparedResult =
          new AnswerMutationResult(answer.getPostId(), answer.getId(), web3.orElse(null));
    }
    if (preparedResult == null
        && answerLifecycleExecutionPort.hasActiveAnswerIntent(answer.getId())) {
      throw new AnswerInvalidInputException(
          "Answer has pending onchain mutation; wait for completion or recover first.");
    }
    if (updatedAnswer != answer) {
      saveAnswerPort.saveAnswer(updatedAnswer);
    }

    if (command.imageIds() != null) {
      updateAnswerImagesPort.updateImages(command.userId(), command.answerId(), command.imageIds());
    }

    return preparedResult == null
        ? new AnswerMutationResult(answer.getPostId(), answer.getId(), null)
        : preparedResult;
  }

  /** Deletes a single answer requested by its owner. */
  @Override
  @Transactional
  public AnswerMutationResult execute(DeleteAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());
    LoadPostPort.PostContext post = loadPost(answer.getPostId());

    answer.validateDeletable(command.userId(), post.answerLocked());
    int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(answer.getPostId()));
    var web3 =
        answerLifecycleExecutionPort.prepareAnswerDelete(
            answer.getPostId(),
            answer.getId(),
            command.userId(),
            post.writerId(),
            post.content(),
            post.reward(),
            activeAnswerCount);
    if (web3.isPresent()) {
      return new AnswerMutationResult(answer.getPostId(), answer.getId(), web3.get());
    }
    deleteAnswerPort.deleteAnswer(answer.getId());
    eventPublisher.publishEvent(new AnswerDeletedEvent(answer.getId()));
    return new AnswerMutationResult(answer.getPostId(), answer.getId(), null);
  }

  /** Deletes all answers belonging to a post after an internal post-deleted event. */
  @Override
  @Transactional
  public void deleteByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    List<Long> answerIds = loadAnswerPort.loadAnswerIdsByPostId(postId);
    deleteAnswerPort.deleteAnswersByPostId(postId);
    answerIds.forEach(answerId -> eventPublisher.publishEvent(new AnswerDeletedEvent(answerId)));
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
      Set<Long> likedAnswerIds,
      Map<Long, momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView>
          web3ExecutionsByAnswerId) {
    return answerReadAssembler.assemble(
        answer,
        writers.get(answer.getUserId()),
        imagesByAnswerId.get(answer.getId()),
        likeCounts.getOrDefault(answer.getId(), 0L),
        likedAnswerIds.contains(answer.getId()),
        web3ExecutionsByAnswerId.get(answer.getId()));
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
