package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerLifecycleAction;

public interface AnswerLifecycleExecutionPort {

  default boolean managesAnswerLifecycle(AnswerLifecycleAction action) {
    return false;
  }

  default boolean hasActiveAnswerIntent(Long answerId) {
    return false;
  }

  default void precheckAnswerCreate(Long postId, String questionContent) {}

  default boolean cancelSignableIntent(String executionIntentId, String reason) {
    return false;
  }

  Optional<AnswerExecutionWriteView> prepareAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  Optional<AnswerExecutionWriteView> recoverAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  Optional<AnswerExecutionWriteView> recoverAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  Optional<AnswerExecutionWriteView> prepareAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  default Optional<AnswerExecutionWriteView> prepareAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount,
      Long updateVersion,
      String updateToken) {
    return prepareAnswerUpdate(
        postId,
        answerId,
        requesterUserId,
        questionWriterUserId,
        questionContent,
        rewardMztk,
        answerContent,
        activeAnswerCount);
  }

  Optional<AnswerExecutionWriteView> prepareAnswerDelete(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      int activeAnswerCount);
}
