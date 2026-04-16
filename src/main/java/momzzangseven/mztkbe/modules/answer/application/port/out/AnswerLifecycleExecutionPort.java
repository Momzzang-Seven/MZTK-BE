package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Optional;

public interface AnswerLifecycleExecutionPort {

  default boolean hasActiveAnswerIntent(Long answerId) {
    return false;
  }

  default void precheckAnswerCreate(Long postId, String questionContent) {}

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

  Optional<AnswerExecutionWriteView> prepareAnswerDelete(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      int activeAnswerCount);
}
