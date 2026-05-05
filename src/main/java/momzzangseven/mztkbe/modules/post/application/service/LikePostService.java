package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.LikePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.PostLikeResult;
import momzzangseven.mztkbe.modules.post.application.port.in.LikePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerLikeTargetPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikePostService implements LikePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final PostLikePersistencePort postLikePersistencePort;
  private final LoadAnswerLikeTargetPort loadAnswerLikeTargetPort;
  private final PostVisibilityPolicy postVisibilityPolicy;

  @Override
  @Transactional
  public PostLikeResult like(LikePostCommand command) {
    command.validate();
    validateTarget(command);

    postLikePersistencePort.insertIfAbsent(
        PostLike.create(command.targetType(), command.targetId(), command.userId()));

    long likeCount =
        postLikePersistencePort.countByTarget(command.targetType(), command.targetId());
    return new PostLikeResult(command.targetType(), command.targetId(), true, likeCount);
  }

  @Override
  @Transactional
  public PostLikeResult unlike(LikePostCommand command) {
    command.validate();
    validateTarget(command);

    postLikePersistencePort.delete(command.targetType(), command.targetId(), command.userId());

    long likeCount =
        postLikePersistencePort.countByTarget(command.targetType(), command.targetId());
    return new PostLikeResult(command.targetType(), command.targetId(), false, likeCount);
  }

  private void validateTarget(LikePostCommand command) {
    if (command.targetType() == PostLikeTargetType.POST) {
      postVisibilityPolicy.validateWritable(
          postPersistencePort.loadPost(command.targetId()).orElseThrow(PostNotFoundException::new));
      return;
    }

    LoadAnswerLikeTargetPort.AnswerLikeTarget answerTarget =
        loadAnswerLikeTargetPort
            .loadAnswerTarget(command.targetId())
            .orElseThrow(AnswerNotFoundException::new);
    if (!answerTarget.postId().equals(command.postId())) {
      throw new AnswerPostMismatchException();
    }
    postVisibilityPolicy.validateWritable(
        postPersistencePort.loadPost(command.postId()).orElseThrow(PostNotFoundException::new));
  }
}
