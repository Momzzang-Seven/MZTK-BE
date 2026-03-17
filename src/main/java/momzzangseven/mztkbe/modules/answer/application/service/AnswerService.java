package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswersByPostUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService
    implements CreateAnswerUseCase,
        GetAnswerUseCase,
        UpdateAnswerUseCase,
        DeleteAnswerUseCase,
        DeleteAnswersByPostUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final LoadAnswerWriterPort loadAnswerWriterPort;

  /** Creates a new answer for a question post. */
  @Override
  @Transactional
  public CreateAnswerResult execute(CreateAnswerCommand command) {
    command.validate();

    LoadPostPort.PostContext post = loadPost(command.postId());
    validateAnswerablePost(post);

    Answer answer =
        Answer.create(
            post.postId(),
            post.writerId(),
            post.isSolved(),
            command.userId(),
            command.content(),
            command.imageUrls());

    Answer savedAnswer = saveAnswerPort.saveAnswer(answer);
    return new CreateAnswerResult(savedAnswer.getId());
  }

  /** Loads answers for a question post together with writer summary fields used by the API. */
  @Override
  @Transactional(readOnly = true)
  public List<AnswerResult> execute(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (!loadPostPort.existsPost(postId)) {
      throw new AnswerPostNotFoundException();
    }

    List<Answer> answers = loadAnswerPort.loadAnswersByPostId(postId);
    Map<Long, LoadAnswerWriterPort.WriterSummary> writers =
        answers.isEmpty()
            ? Map.of()
            : loadAnswerWriterPort.loadWritersByIds(
                answers.stream().map(Answer::getUserId).distinct().toList());

    return answers.stream().map(answer -> toResult(answer, writers)).toList();
  }

  /** Updates mutable answer fields. Omitted fields are preserved. */
  @Override
  @Transactional
  public void execute(UpdateAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswer(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    Answer updatedAnswer = answer.update(command.content(), command.imageUrls(), command.userId());
    saveAnswerPort.saveAnswer(updatedAnswer);
  }

  /** Deletes a single answer requested by its owner. */
  @Override
  @Transactional
  public void execute(DeleteAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswer(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    answer.validateDeletable(command.userId());
    deleteAnswerPort.deleteAnswer(answer.getId());
  }

  /** Deletes all answers belonging to a post after an internal post-deleted event. */
  @Override
  @Transactional
  public void deleteByPostId(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    deleteAnswerPort.deleteAnswersByPostId(postId);
  }

  private LoadPostPort.PostContext loadPost(Long postId) {
    return loadPostPort.loadPost(postId).orElseThrow(AnswerPostNotFoundException::new);
  }

  private Answer loadAnswer(Long answerId) {
    return loadAnswerPort.loadAnswer(answerId).orElseThrow(AnswerNotFoundException::new);
  }

  private AnswerResult toResult(
      Answer answer, Map<Long, LoadAnswerWriterPort.WriterSummary> writers) {
    LoadAnswerWriterPort.WriterSummary writer = writers.get(answer.getUserId());
    return AnswerResult.from(
        answer,
        writer != null ? writer.nickname() : null,
        writer != null ? writer.profileImageUrl() : null);
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
