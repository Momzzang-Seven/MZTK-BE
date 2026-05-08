package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;

public interface QuestionLifecycleExecutionPort {

  default boolean managesAcceptLifecycle() {
    return false;
  }

  default boolean managesQuestionCreateLifecycle() {
    return false;
  }

  default boolean hasActiveQuestionIntent(Long postId) {
    return false;
  }

  default Optional<QuestionUpdateStatePreparation> beginQuestionUpdateState(
      Long postId, Long requesterUserId, String questionContent) {
    return Optional.empty();
  }

  void precheckQuestionCreate(Long requesterUserId, Long rewardMztk);

  Optional<QuestionExecutionWriteView> prepareQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  Optional<QuestionExecutionWriteView> recoverQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  Optional<QuestionExecutionWriteView> recoverQuestionUpdate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  Optional<QuestionExecutionWriteView> prepareQuestionUpdate(
      Long postId,
      Long requesterUserId,
      String questionContent,
      Long rewardMztk,
      Long questionUpdateVersion,
      String questionUpdateToken);

  Optional<QuestionExecutionWriteView> prepareQuestionDelete(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  Optional<QuestionExecutionWriteView> prepareAnswerAccept(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long answerWriterUserId,
      String questionContent,
      String answerContent,
      Long rewardMztk);

  record QuestionUpdateStatePreparation(
      Long postId, Long updateVersion, String updateToken, String expectedQuestionHash) {}
}
