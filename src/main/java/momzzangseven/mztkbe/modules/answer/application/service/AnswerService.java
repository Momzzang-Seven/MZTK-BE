package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswersByPostUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService
    implements CreateAnswerUseCase,
        GetAnswerUseCase,
        GetAnswerSummaryUseCase,
        UpdateAnswerUseCase,
        DeleteAnswerUseCase,
        DeleteAnswersByPostUseCase,
        MarkAnswerAcceptedUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final LoadAnswerWriterPort loadAnswerWriterPort;
  private final LoadAnswerImagesPort loadAnswerImagesPort;
  private final UpdateAnswerImagesPort updateAnswerImagesPort;
  private final AnswerReadAssembler answerReadAssembler;
  private final ApplicationEventPublisher eventPublisher;

  /** Creates a new answer for a question post. */
  @Override
  @Transactional
  public CreateAnswerResult execute(CreateAnswerCommand command) {
    command.validate();

    LoadPostPort.PostContext post = loadPost(command.postId());
    validateAnswerablePost(post);

    Answer answer =
        Answer.create(
            post.postId(), post.writerId(), post.isSolved(), command.userId(), command.content());

    Answer savedAnswer = saveAnswerPort.saveAnswer(answer);

    if (command.imageIds() != null && !command.imageIds().isEmpty()) {
      updateAnswerImagesPort.updateImages(
          savedAnswer.getUserId(), savedAnswer.getId(), command.imageIds());
    }

    return new CreateAnswerResult(savedAnswer.getId());
  }

  /** Loads answers for a question post together with writer summary fields used by the API. */
  @Override
  @Transactional(readOnly = true)
  public List<AnswerResult> execute(Long postId) {
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

    return answers.stream().map(answer -> toResult(answer, writers, imagesByAnswerId)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<GetAnswerSummaryUseCase.AnswerSummary> getAnswerSummary(Long answerId) {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    return loadAnswerPort
        .loadAnswer(answerId)
        .map(
            answer ->
                new GetAnswerSummaryUseCase.AnswerSummary(
                    answer.getId(), answer.getPostId(), answer.getUserId()));
  }

  /** Updates mutable answer fields. Omitted fields are preserved. */
  @Override
  @Transactional
  public void execute(UpdateAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    Answer updatedAnswer = answer.update(command.content(), command.userId());
    if (updatedAnswer != answer) {
      saveAnswerPort.saveAnswer(updatedAnswer);
    }

    if (command.imageIds() != null) {
      updateAnswerImagesPort.updateImages(command.userId(), command.answerId(), command.imageIds());
    }
  }

  /** Deletes a single answer requested by its owner. */
  @Override
  @Transactional
  public void execute(DeleteAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswerForUpdate(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    answer.validateDeletable(command.userId());
    deleteAnswerPort.deleteAnswer(answer.getId());
    eventPublisher.publishEvent(new AnswerDeletedEvent(answer.getId()));
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
      Map<Long, AnswerImageResult> imagesByAnswerId) {
    return answerReadAssembler.assemble(
        answer, writers.get(answer.getUserId()), imagesByAnswerId.get(answer.getId()));
  }

  private void validateAnswerBelongsToPost(Answer answer, Long postId) {
    if (!answer.getPostId().equals(postId)) {
      throw new AnswerPostMismatchException();
    }
  }

  private void validateAnswerablePost(LoadPostPort.PostContext post) {
    if (!post.questionPost()) {
      throw new AnswerUnsupportedPostTypeException();
    }
  }
}
