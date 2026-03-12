package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
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
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService
    implements CreateAnswerUseCase, GetAnswerUseCase, UpdateAnswerUseCase, DeleteAnswerUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;

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

  @Override
  @Transactional(readOnly = true)
  public List<AnswerResult> execute(Long postId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }

    List<Answer> answers = loadAnswerPort.loadAnswersByPostId(postId);
    if (answers.isEmpty() && !loadPostPort.existsPost(postId)) {
      throw new AnswerPostNotFoundException();
    }

    return answers.stream().map(AnswerResult::from).toList();
  }

  @Override
  @Transactional
  public void execute(UpdateAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswer(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    Answer updatedAnswer = answer.update(command.content(), command.imageUrls(), command.userId());
    saveAnswerPort.saveAnswer(updatedAnswer);
  }

  @Override
  @Transactional
  public void execute(DeleteAnswerCommand command) {
    command.validate();

    Answer answer = loadAnswer(command.answerId());
    validateAnswerBelongsToPost(answer, command.postId());

    answer.validateDeletable(command.userId());
    deleteAnswerPort.deleteAnswer(answer.getId());
  }

  private LoadPostPort.PostContext loadPost(Long postId) {
    return loadPostPort.loadPost(postId).orElseThrow(AnswerPostNotFoundException::new);
  }

  private Answer loadAnswer(Long answerId) {
    return loadAnswerPort.loadAnswer(answerId).orElseThrow(AnswerNotFoundException::new);
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
