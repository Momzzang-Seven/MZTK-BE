package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.post.AnswerNotBelongToPostException;
import momzzangseven.mztkbe.global.error.post.InvalidPostTypeException;
import momzzangseven.mztkbe.global.error.post.OnlyPostWriterCanAcceptException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerCommand;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcceptAnswerService implements AcceptAnswerUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadAcceptedAnswerPort loadAcceptedAnswerPort;
  private final MarkAcceptedAnswerPort markAcceptedAnswerPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Override
  @Transactional
  public AcceptAnswerResult execute(AcceptAnswerCommand command) {
    command.validate();

    Post post =
        postPersistencePort
            .loadPostForUpdate(command.postId())
            .orElseThrow(PostNotFoundException::new);
    LoadAcceptedAnswerPort.AcceptedAnswerInfo answer =
        loadAcceptedAnswerPort
            .loadAcceptedAnswer(command.answerId())
            .orElseThrow(AnswerNotFoundException::new);

    validateQuestionPost(post);
    validatePostWriter(post, command.requesterId());
    validateAnswerBelongsToPost(post, answer);
    Post acceptedPost = post.accept(command.answerId());
    Post savedPost = postPersistencePort.savePost(acceptedPost);
    markAcceptedAnswerPort.markAccepted(answer.answerId());
    questionLifecycleExecutionPort.prepareAnswerAccept(
        savedPost.getId(),
        answer.answerId(),
        command.requesterId(),
        answer.userId(),
        savedPost.getContent(),
        answer.content(),
        savedPost.getReward());
    return AcceptAnswerResult.from(savedPost);
  }

  private void validateQuestionPost(Post post) {
    if (post.getType() != PostType.QUESTION) {
      throw new InvalidPostTypeException();
    }
  }

  private void validatePostWriter(Post post, Long requesterId) {
    if (!post.getUserId().equals(requesterId)) {
      throw new OnlyPostWriterCanAcceptException();
    }
  }

  private void validateAnswerBelongsToPost(
      Post post, LoadAcceptedAnswerPort.AcceptedAnswerInfo answer) {
    if (!post.getId().equals(answer.postId())) {
      throw new AnswerNotBelongToPostException();
    }
  }
}
