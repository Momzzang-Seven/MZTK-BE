package momzzangseven.mztkbe.modules.post.application.port.out;

public interface QuestionLifecycleExecutionPort {

  default boolean managesAcceptLifecycle() {
    return false;
  }

  void precheckQuestionCreate(Long requesterUserId, Long rewardMztk);

  void prepareQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  void prepareQuestionUpdate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  void prepareQuestionDelete(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk);

  void prepareAnswerAccept(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long answerWriterUserId,
      String questionContent,
      String answerContent,
      Long rewardMztk);
}
